(ns mana.core
  (:gen-class)
  (:require [mana.tools :as tools]
            [mana.agent :as agent]
            [mana.tasks :as tasks]))

(def ollama-api-key (System/getenv "OLLAMA_API_KEY"))

(def qwen3-6-fast
  {:url "http://localhost:3000/v1/chat/completions"
   :model "qwen/qwen3.6-35b-a3b"
   :window 200000})

; Capabilities -> Workflows
;(defmacro capability [cfg task base-args]
;  `(fn [args]
;     (agent/agent-loop ~cfg (~task (merge args ~base-args)))))

(defn capability [cfg task base-args]
  (fn [args]
    (agent/agent-loop cfg (task (merge args base-args)))))

(def search (capability qwen3-6-fast tasks/research { :ollama-api-key ollama-api-key :search-limit 5 :max-turns 10}))
(def recall (capability qwen3-6-fast tasks/remember { :max-turns 100 }))
(def summarize (capability qwen3-6-fast tasks/condense { :max-turns 1 }))

(defn bug-hunt [bug-desc]
  (format "You are searching for a bug, i.e. an issue or error, in the code nested in the current directory.
Use the tools available to you to search the web for likely causes of the bug described by the user.
You are done when you have identified at least three sources of information that may inform an understanding of the cuase of the issue.

Searches you should prioritize:
- Official documentation

Description of the issue:
%s" bug-desc))

(defn find-bug [s-issue-desc]
  "Find a bug in some code."
  (let [online-resources (search {:query (bug-hunt s-issue-desc)})
        project-memories (recall {:tags [:mana-source-code :bugs :skills]})]
    (summarize {:contents [online-resources project-memories]})))

(defn -main [& args]
  (println (find-bug "This is a test. Don't search for anything")))
