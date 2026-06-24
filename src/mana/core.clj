(ns mana.core
  (:gen-class)
  (:require [mana.tools :as tools]
            [mana.agent :as agent]))

(def local-config
  {:url "http://localhost:3000/v1/chat/completions"
   :key ""
   :model "qwen/qwen3.6-35b-a3b"})

(def tool-registry
  [{:name "display" :description tools/display-description       :implementation tools/display}
   {:name "input"   :description tools/request-input-description :implementation tools/request-input}
   {:name "read"    :description tools/read-file-description     :implementation tools/read-file}
   {:name "list"    :description tools/list-files-description    :implementation tools/list-files}])

(defn -main [& args]
  (agent/agent-loop local-config tool-registry (first args)))
