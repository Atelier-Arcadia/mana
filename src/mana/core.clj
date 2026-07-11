(ns mana.core
  (:gen-class)
  (:require [mana.tools :as tools]
            [mana.agent :as agent]
            [mana.tasks :as tasks]
            [mana.chats :as chats]
            [mana.mana :as mana]))

(def secrets
  (-> "secrets.edn"
      (slurp)
      (clojure.edn/read-string)))

(def ollama-api-key (:ollama-api-key secrets))

(def qwen-3-6-35b
  {:url "http://localhost:3000/v1/chat/completions"
   :model "qwen/qwen3.6-35b-a3b"
   :window 200000})

(def ministral-3-14b
  {:url "http://localhost:3000/v1/chat/completions"
   :model "mistralai/ministral-3-14b-reasoning"
   :window 250000})

(def chosen-model ministral-3-14b)

; TODO - track total token spend to inform compaction
(def ctx (atom [mana/personality]))

(defn- action [task base-args]
  (fn [args] (task (merge base-args args))))

(defn- first-respond-message [history]
  (let [respond? #(and (chats/is_a? :tool-call-message %)
                       (= (chats/tool-being-called %) "respond"))]
    (->> history
         (filter respond?)
         (first))))

(defn- first-assistant-message [history]
  (->> history
       (filter (partial chats/is_a? :assistant-message))
       (first)))

(defn- displayed-message [tool-call-msg]
  (get-in tool-call-msg [:function_call :arguments "message"]))


(def search (action tasks/research {:ollama-api-key ollama-api-key :max-turns 100 :search-limit 10}))
(def summarize (action tasks/condense {:max-turns 2}))


; Perform a task. Expects respond to be called to output the result.
(defn act [action args]
  (let [task (action args)
        history (agent/tool-calling chosen-model @ctx task)
        compacted (agent/tool-calling chosen-model [] (summarize {:contents history}))
        response (first-respond-message history)]
    (swap! ctx conj (first-respond-message compacted))
    (displayed-message response)))

; Exchange a single message and produce the response.
(defn say [prompt]
  (let [user (chats/user-message prompt)
        mana (agent/conversational chosen-model @ctx user)]
    (swap! ctx conj user mana)
    (:content mana)))

(defn clear [] (reset! ctx [mana/personality]))

(defn compact []
  (let [history (agent/tool-calling chosen-model [] (summarize {:contents @ctx}))
        response (first-respond-message history)]
    (reset! ctx [mana/personality response])))

(defn -main [& args]
  (println (act search {:query "What is rich hickey up to in 2026?"})))
