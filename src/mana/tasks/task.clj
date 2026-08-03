(ns mana.tasks.task
  (:require [mana.inference :as chat]
            [mana.functions :as fx]
            [clojure.core.async :as async :refer [>!! <!!]]
            [cheshire.core :as json]))


; Events
(defn- model-responded [response]
  {:event :tool-call-request
   :model-response response})

(defn- guidance-provided [prompt]
  {:event :guidance-provided
   :new-message (chat/user-message prompt)})

(defn- tool-call-approved [state]
  {:event :tool-call-approved
   :new-state state})

(defn- tool-call-dispatched [result]
  {:event :tool-call-dispatched
   :data result})

(defn- errored [cause]
  {:event :error
   :cause cause})

(defn- completed [state]
  {:event :completed
   :final-state state})

; Transitions
(defmulti transition
  (fn [_ event]
    (get event :event)))

(defmethod transition :tool-call-request [{state :state context :context usage :usage} event]
  (let [code (get-in event [:model-response :code])]
    {:mode :update
     :state state
     :context (conj context (chat/assistant-message (str code)))
     :usage (merge-with + usage (get-in event [:model-response :usage]))
     :tool-call code}))

(defmethod transition :guidance-provided [{state :state context :context usage :usage} event]
  {:mode :prompt
   :state state
   :context (conj context (:new-message event))
   :usage usage})

(defmethod transition :tool-call-approved [{context :context usage :usage code :tool-call} event]
  {:mode :tool-call
   :state (:new-state event)
   :context context
   :usage usage
   :tool-call code})

(defmethod transition :tool-call-dispatched [{state :state context :context usage :usage} event]
  {:mode :prompt
   :state state
   :context (conj context (chat/user-message (json/generate-string (:data event))))
   :usage usage})

(defmethod transition :error [{state :state context :context usage :usage} event]
  {:mode :error
   :state state
   :context context
   :usage usage
   :cause (:cause event)})

(defmethod transition :completed [{context :context usage :usage} event]
  {:mode :complete
   :state (:final-state event)
   :context context
   :usage usage})

; State Machine
(defmulti step-function
  (fn [model-dispatch machine-state task]
    (:mode machine-state)))

(defmethod step-function :prompt [model-dispatch machine-state task]
  (model-responded (chat/perform model-dispatch (:tools task) (:context machine-state))))

(defmethod step-function :update [model-dispatch machine-state task]
  (let [{update-fn :update} task
        {ctx :context state :state tool-call :tool-call} machine-state]
    (update-fn ctx state tool-call)))

(defmethod step-function :tool-call [model-dispatch machine-state task]
  (tool-call-dispatched (fx/dispatch (:tools task) (:tool-call machine-state))))

(defmethod step-function :error [model-dispatch machine-state task]
  nil)

(defmethod step-function :complete [model-dispatch machine-state task]
  nil)

(defn- initialize [context task]
  {:mode    :prompt
   :state   ((:init task))
   :context (conj context (chat/user-message (:prompt task)))
   :usage   {:input-tokens 0 :output-tokens 0}})

(defn run-thread [model-dispatch context task]
  (async/thread
    (loop [machine (initialize context task)
           event   (step-function model-dispatch machine task)]
      (if event
        (let [new-state (transition machine event)]
          (recur new-state (step-function model-dispatch new-state task)))
        machine))))

; Update function responses
(defn guide [prompt]
  (guidance-provided prompt))

(defn error [cause]
  (errored cause))

(defn complete [state]
  (completed state))

(defn allow [new-state]
  (tool-call-approved new-state))
