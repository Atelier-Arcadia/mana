(ns mana.core
  (:gen-class)
  (:require [mana.prompts :as prompts]
            [mana.inference :as chat]
            [mana.functions :as fx]
            [mana.tasks.library :as tasks]
            [mana.tasks.supervisor.supervision :as spv]
            [mana.tasks.supervisor.recovery :as recover]
            [cheshire.core :as json]
            [clojure.core.async :refer [>!! <!!]]))

(def context (atom []))
(def tool-request (atom nil))
(def usage (atom {:input-tokens 0 :output-tokens 0}))

(def mana (chat/dispatcher))
(def supervisor (spv/supervisor mana))

(spv/run-thread supervisor)

(def fs [fx/read-file fx/list-directory])

(defn clear! []
  (reset! context []))

(defn stop! []
  (chat/stop mana))

(defn summarize [task result]
  (let [request (conj (:context result)
                      (chat/user-message (prompts/task-summarization result)))
        {text :text} (chat/converse mana request)] ; TODO - what to do with cost?
    (swap! context conj (chat/user-message (prompts/task-summary task text)))))

(defn say [prompt]
  (swap! context conj (chat/user-message prompt))
  (let [{cost :usage text :text} (chat/converse mana @context)]
    (swap! usage #(merge-with + % cost))
    (swap! context conj (chat/assistant-message text))
    (println text)))

(defn act [tools prompt]
  (swap! context conj (chat/user-message prompt))
  (let [{cost :usage code :code} (chat/perform mana tools @context)]
    (reset! tool-request {:code code :tools tools})
    (swap! usage #(merge-with + % cost))
    (swap! context conj (chat/assistant-message (str code)))
    (println (format "---\nTool call requested:\n%s\n" code))))

(defn y []
  (let [{tools :tools code :code} @tool-request
        result (fx/dispatch tools code)]
    (reset! tool-request nil)
    (swap! context conj (chat/user-message (json/generate-string result)))
    result))

(defn n []
  (reset! tool-request nil)
  (swap! context conj (chat/user-message "Denied tool call")))

(defn !
  ([] (stop!))
  ([p] (say p))
  ([t p] (act t p)))

(defmacro !! [code]
  `(let [fmt# (format "```clojure\n%s\n```" '~code)
         result# ~code]
     (swap! context conj (chat/user-message (format "The user shared code with you:\n\n%s\nResult: %s" fmt# result#)))
     result#))

(defn spawn
  ([task] (spawn task [] recover/bruteforce))
  ([task context] (spawn task context recover/bruteforce))
  ([task context recover]
    (spv/monitor supervisor {:task task :context context :on-error recover :on-complete summarize})))
