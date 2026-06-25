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
  [{:name "display" :description tools/display-description       :implementation tools/display}
   {:name "input"   :description tools/request-input-description :implementation tools/request-input}
   {:name "read"    :description tools/read-file-description     :implementation tools/read-file}
   {:name "list"    :description tools/list-files-description    :implementation tools/list-files}])

(def ollama-web-tools
  (if ollama-api-key
    [{:name "search" :description tools/web-search-description :implementation (tools/create-web-search ollama-api-key)}
     {:name "fetch"  :description tools/web-fetch-description  :implementation (tools/create-web-fetch ollama-api-key)}]
    []))

(defn -main [& args]
  (let [all-tools (into tool-registry ollama-web-tools)]
    (agent/agent-loop local-config all-tools (first args))))
