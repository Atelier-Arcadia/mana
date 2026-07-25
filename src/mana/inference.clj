(ns mana.inference
  (:require [mana.prompts :as prompts]
            [clojure.edn :as edn]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.core.async :refer [thread]]))

; The main, plain-jane inference method that goes straight to the responses API.
(defn- inference [api model msg-history]
  (let [body (json/generate-string {:model model :input msg-history} {:pretty true})
        req {:content-type :json :body body}
        res (http/post api req)]
    (json/parse-string (:body res))))

; Helpers to extract information from responses
(defn- assistant-message-complete? [{ role "role" status "status" }]
  (and (= status "completed")
       (= role "assistant")))

(defn- output-type-text? [{type "type"}]
  (= type "output_text"))

(defn- usage [{ {itkn "input_tokens" otkn "output_tokens"} "usage" }]
  {:input-tokens itkn :output-tokens otkn})

(defn- text [{ outputs "output" }]
  (let [completed (filter assistant-message-complete? outputs)
        contents (flatten (map #(get % "content") completed))
        text-outputs (filter output-type-text? contents)
        all-text (map #(get % "text") text-outputs)]
    (clojure.string/join " " all-text)))

; Message types for simplicity
(defn- message [role content]
  {:role role :content content})

(def system-message (partial message "system"))
(def user-message (partial message "user"))
(defn assistant-message [content]
  (assoc (message "assistant" content) :type "message"))

; Public interface for doing inference
(defn- prompt-model [api model messages]
  (let [response (inference api model messages)]
    {:usage (usage response)
     :text (text response)}))

(defn converse [api model msgs]
  (let [system-prompt [(system-message prompts/conversational)]
        response      (prompt-model api model (into system-prompt msgs))]
    response))

(defn tool-call [api model available-tools msgs]
  (let [system-prompt [(system-message (prompts/tool-calling available-tools))]
        response      (prompt-model api model (into system-prompt msgs))
        with-code     (assoc response :code (edn/read-string (:text response)))]
    (dissoc with-code :text)))
