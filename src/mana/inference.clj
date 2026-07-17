(ns mana.inference
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

; The main, plain-jane inference method that goes straight to the responses API.
(defn inference [api model tools msg-history]
  (let [body (json/generate-string {:model model :input msg-history})
        req {:content-type :json :body body}
        res (http/post api req)]
    (json/parse-string (:body res))))

; Helpers to extract information from responses
(defn- assistant-message-complete? [{ role "role" status "status" }]
  (and (= status "completed")
       (= role "assistant")))

(defn- output-type-text? [{type "type"}]
  (= type "output_text"))

(defn usage [{ {itkn "input_tokens" otkn "output_tokens"} "usage" }]
  {:input-tokens itkn :output-tokens otkn})

(defn text [{ outputs "output" }]
  (let [completed (filter assistant-message-complete? outputs)
        contents (flatten (map #(get % "content") completed))
        text-outputs (filter output-type-text? contents)
        all-text (map #(get % "text") text-outputs)]
    (clojure.string/join " " all-text)))

; Message types for simplicity
(defn message [role content]
  {:role role :content content :type "message"})

(def system-message (partial message "system"))
(def user-message (partial message "user"))
(def assistant-message (partial message "assistant"))
