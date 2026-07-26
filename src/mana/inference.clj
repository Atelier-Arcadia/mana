(ns mana.inference
  (:require [mana.prompts :as prompts]
            [clojure.edn :as edn]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.core.async :refer [thread chan >!! <!!]]))

(def api "http://localhost:3000/v1/responses")
(def model "mistralai/devstral-small-2-2512")

; The main, plain-jane inference method that goes straight to the responses API.
(defn- inference [msg-history]
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

(def debug-log (atom []))

(defn log [& parts]
  (swap! debug-log conj (apply str parts)))

; Public interface for doing inference
(defn- prompt-model [messages]
  (log "Attempting to prompt model with messages" messages)
  (let [response (inference messages)]
    {:usage (usage response)
     :text (text response)}))

(defn- one-shot [msgs]
  (let [system-prompt [(system-message prompts/conversational)]
        response      (prompt-model (into system-prompt msgs))]
    response))

(defn- tool-call [available-tools msgs]
  (let [system-prompt [(system-message (prompts/tool-calling available-tools))]
        response      (prompt-model (into system-prompt msgs))
        with-code     (assoc response :code (edn/read-string (:text response)))]
    (dissoc with-code :text)))

(defn- request-inference [to-dispatcher request]
  (let [read-response (chan)]
    (>!! to-dispatcher {:respond read-response :request request})
    (<!! read-response)))

(defn dispatcher []
  (let [requests (chan)]
    (thread (loop [{res :respond req :request} (<!! requests)]
              (case (:kind req)
                :stop     nil
                :converse (do (>!! res (one-shot (:messages req)))
                              (recur (<!! requests)))
                :perform  (do (>!! res (tool-call (:tools req) (:messages req)))
                              (recur (<!! requests))))))
    requests))

(defn stop [dispatch-chan]
  (>!! dispatch-chan {:kind :stop}))

(defn converse [dispatch-chan messages]
  (request-inference dispatch-chan {:kind :converse :messages messages}))

(defn perform [dispatch-chan tools messages]
  (request-inference dispatch-chan {:kind :perform :tools tools :messages messages}))
