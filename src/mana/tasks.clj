(ns mana.tasks
  (:require [mana.inference :as chat]
            [mana.functions :as fx]
            [cheshire.core :as json]
            [clojure.core.async :as async :refer [>!! <!!]])
  (:import [java.nio.file Paths]))

(def debug-log (atom []))

(defn log [& parts]
  (swap! debug-log conj (apply str parts)))

(defn- request-summary [state]
  (chat/user-message (format "Please summarize our conversation.
Identify the following critical pieces of information:
1. What was the original task you were asked to complete?
2. What steps did you take?
3. What issues or errors did you encounter?
4. How did you course-correct when you encountered problems?

The final state of the task was:
```json
%s
```

Conclude with a report to satisfy the task you were given." (json/generate-string state))))

(defn- summarize [dx ctx usage state]
  (let [response    (chat/converse dx (conj ctx (request-summary state)))
        total-usage (merge-with + (:usage response) usage)]
    (merge response {:usage total-usage})))

(defn task [model-dispatch { id :id prompt :prompt tools :tools init :init update-fn :update }]
  (async/thread
    (loop [state (init)
           context [prompt]
           usage {:input-tokens 0 :output-tokens 0}]
      (let [response  (chat/perform model-dispatch tools context)
            new-ctx   (conj context (chat/assistant-message (str (:code response))))
            new-usage (merge-with + (:usage response) usage)
            next-step (update-fn new-ctx state response)]
        (case (:kind next-step)
          :error    next-step
          :complete (summarize model-dispatch new-ctx new-usage (:state next-step))
          :guidance (recur state
                           (conj new-ctx (chat/user-message (:prompt next-step)))
                           new-usage)
          :allow    (let [tool-result (fx/dispatch tools (:code response))
                          new-message (chat/user-message (json/generate-string tool-result))]
                      (recur (:state next-step)
                             (conj new-ctx new-message)
                             new-usage)))))))

(defn- monitor [dx {tsk :task :as process}]
  (let [result-chan (task dx tsk)]
    (log "Monitoring a new task")
    (async/map (fn [result] (assoc process :result result))
               [result-chan])))

(defn- handle-termination [sup-mix dx {tsk :task result :result recover :recovery handler :on-complete}]
  (log "Got termination for task " tsk)
  (if (and (contains? result :kind)
           (= (:kind result) :error))
    (->> tsk
         (recover (:cause result) result)
         (monitor dx)
         (async/admix sup-mix))
    (handler tsk result)))

(defn supervisor [model-dispatch]
  (let [registrations (async/chan)
        task-status (async/chan)
        supervised (async/mix task-status)]
    (async/thread
      (loop [[value port] (async/alts!! [registrations task-status])]
        (if (= port registrations)
          (do (log "adding a new task to the supervisor")
              (async/admix supervised (monitor model-dispatch value))
              (recur (async/alts!! [registrations task-status])))
          (do (log "Got a completion result")
              (handle-termination supervised model-dispatch value)
              (recur (async/alts!! [registrations task-status]))))))
    registrations))

(defn register [reg-chan {tsk :task recover :recovery handler :on-complete :as process}]
  (>!! reg-chan process))

(defn retry-exactly [_failure-cause _error-data original-task]
  original-task)

(defn guide [prompt]
  {:kind :guidance :prompt prompt})

(defn error [cause context state]
  {:kind :error :cause cause :context context :state state})

(defn complete [state]
  {:kind :complete :state state})

(defn allow [new-state]
  {:kind :allow :state new-state})

(defn- tool-call? [{tool-name :name} {code :code}]
  (= tool-name (name (first code))))

(defn- arguments [{code :code}]
  (second code))

(defn- child-path? [parent child]
  (let [parent-path (.normalize (Paths/get parent (into-array String [])))
        child-path  (.normalize (Paths/get child (into-array String [])))]
    (.startsWith child-path parent-path)))

(defn explore-prompt [user-prompt dir]
  (chat/user-message (format "Explore the filesystem to develop an understanding of its contents.
Your goal is to build up enough information based on facts found in the files you read to address the user's request.
You are done when you have clear information that allows you to respond to the user's request grounded in factual information.

Your search is constrained to the %s directory and its children.

Request from user: %s" dir user-prompt)))

(defn explore-updater [context state tool-call]
  (let [{ fr :files-read dir :directory max :max-reads } state
        finished?       (tool-call? fx/report-finished tool-call)
        read-file?      (tool-call? fx/read-file tool-call)
        file-to-read    (get (arguments tool-call) :file-name)
        has-been-read?  (and read-file? (contains? fr file-to-read))
        outside-dir?    (and read-file? (not (child-path? dir file-to-read)))
        limit-exceeded? (>= (count fr) max)]
    (cond finished?       (complete state)
          limit-exceeded? (error :limit-exceeded context state)
          outside-dir?    (guide (format "You are only allowed to read files within the %s directory" dir))
          has-been-read?  (guide "You have already read this file.")
          read-file?      (allow (update state :files-read conj file-to-read))
          :else           (allow state))))

(defn explore [{ goal :goal dir :directory max :max-reads }]
  {:id :explore
   :prompt (explore-prompt goal dir)
   :tools [fx/report-finished fx/read-file fx/list-directory]
   :init (fn [] {:files-read #{}
                 :directory dir
                 :max-reads max})
   :update explore-updater})
