(ns mana.core
  (:gen-class)
  (:require [mana.tools :as tools]
            [mana.agent :as agent]
            [mana.tasks :as tasks]
            [mana.chats :as chats]))

(def secrets
  (-> "secrets.edn"
      (slurp)
      (clojure.edn/read-string)))

(def ollama-api-key (:ollama-api-key secrets))

(def qwen3-6-fast
  {:url "http://localhost:3000/v1/chat/completions"
   :model "qwen/qwen3.6-35b-a3b"
   :window 200000})

(def ctx (atom (chats/user-message "")))

(defn act [task]
  (agent/tool-calling qwen3-6-fast @ctx task))

(defn say [prompt]
  (agent/conversational qwen3-6-fast @ctx (chats/user-message prompt)))
