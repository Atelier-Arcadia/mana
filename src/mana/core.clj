(ns mana.core
  (:gen-class)
  (:require [mana.tools :as tools]
            [mana.agent :as agent]))

(def ollama-api-key (System/getenv "OLLAMA_API_KEY"))

(def local-config
  {:url "http://localhost:3000/v1/chat/completions"
   :key ""
   :model "qwen/qwen3.6-35b-a3b"})

(def tool-registry
  [tools/display
   tools/read-file
   tools/list-files
   tools/request-input])

(def ollama-web-tools
  (if ollama-api-key
    [(tools/create-web-search ollama-api-key)
     (tools/create-web-fetch ollama-api-key)]
    []))

(defn -main [& args]
  (let [all-tools (into tool-registry ollama-web-tools)]
    (agent/agent-loop local-config all-tools (first args))))
