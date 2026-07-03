(ns mana.core
  (:gen-class)
  (:require [mana.tools :as tools]
            [mana.agent :as agent]
            [mana.tasks :as tasks]))

(def ollama-api-key (System/getenv "OLLAMA_API_KEY"))

(def local-config
  {:url "http://localhost:3000/v1/chat/completions"
   :model "qwen/qwen3.6-35b-a3b"
   :window 200000})

(defn -main [& args]
  (agent/agent-loop
   local-config
   (tasks/research {:query (first args)
                    :search-limit 5
                    :ollama-api-key ollama-api-key})))
