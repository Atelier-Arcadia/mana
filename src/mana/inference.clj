(ns mana.inference
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(alias 'str 'clojure.string)

(defn- message [role msgs]
  { :role role :content msgs })

(defn- extract-single-tool-call [tool-call-json]
  (let [name (get-in tool-call-json ["function" "name"])
        args-json (get-in tool-call-json ["function" "arguments"])
        arguments (json/parse-string args-json)]
    {:name name
     :arguments arguments}))

(defn- extract-tool-calls [json-data]
  (let [choices (get json-data "choices")
        tool-call-data (flatten (map #(get-in % ["message" "tool_calls"]) choices))]
    (map extract-single-tool-call tool-call-data)))

(defn- extract-usage-data [json-data]
  (let [usage (get json-data "usage")]
    {:input-tokens (get usage "prompt_tokens")
     :output-tokens (get usage "completion_tokens")}))

(defn- extract-reasoning-content [json-data]
  (let [choices (get json-data "choices")]
    (str/join "\n" (map #(get-in % ["message" "reasoning_content"]) choices))))

(def user-message #(message "user" %))
(def assistant-message #(message "assistant" %))
(def system-message #(message "system" %))
(def tool-result-message #(message "tool" %))

(defn tool-call-message [{name :name schema :schema}]
  {:role "assistant"
   :function_call {:name name :arguments schema}})

(defn- schema [{name :name desc :description schema :schema}]
  {:type "function"
   :function {:name name
              :description desc
              :parameters schema}})

(defn inference
  [{url :url model :model} tools messages]
  "Perform inference/completion with the configured model via a server hosting said model."
  (let [body (json/generate-string
              {:model model
               :messages messages
               :tools (map schema tools)}
              {:pretty true})
        req {:accept :json
             :content-type :json
             :socket-timeout 300000
             :connection-timeout 300000
             :body body}
        res (http/post url req)
        data (json/parse-string (:body res))]
    (into (extract-usage-data data)
          {:tool-calls (extract-tool-calls data)
           :thoughts (extract-reasoning-content data)})))
