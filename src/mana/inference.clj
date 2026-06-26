(ns mana.inference
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(alias 'str 'clojure.string)

(defn- message [role msgs]
  { :role role :content msgs })

(defn- text-content [text]
  { :type "text" :text text })

; TODO - Not using anymore; dead code?
(defn- extract-text-response [json-data]
  (str/join
   ""
   (map (fn [choice] (get-in choice ["message" "content"]))
        (get-in json-data ["choices"]))))

(defn- extract-single-tool-call [tool-call-json]
  (let [name (get-in tool-call-json ["function" "name"])
        args-json (get-in tool-call-json ["function" "arguments"])
        arguments (get (json/parse-string args-json) "arguments")]
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

(def user-message #(message "user" %))
(def assistant-message #(message "assistant" %))
(def system-message #(message "system" %))
(def tool-result-message #(message "tool" %))

; TODO - Refactor code so we pass messages around rather than JSON data we have to parse through.
(defn tool-call-message [tool-call]
  {:role "assistant"
   :function_call {:name (:name tool-call)
                   :arguments {:arguments (:arguments tool-call)}}}) ; TODO - schemas :3

; TODO - Provide real schemas for tools.
(defn- tool-schemas [tools]
  (map (fn [tool]
         {:type "function"
          :function {:name (:name tool)
                     :description (:description tool)
                     :parameters {:type "object"
                                  :description "generic arguments interpreted by the tool"
                                  :properties {"arguments" {:type "array" :items "string"}}}}})
       tools))

(defn inference
  [{url :url model :model} tools messages]
  "Perform inference/completion with MiniMax-M3. Creates a hash with
* :text
* :input-tokens
* :output-tokens"
  (let [body (json/generate-string
              {:model model
               :messages messages
               :tools (tool-schemas tools)}
              {:pretty true})
        req {:accept :json
             :content-type :json
             :socket-timeout 300000
             :connection-timeout 300000
             :body body}
        res (http/post url req)
        data (json/parse-string (get res :body))]
    (assoc (extract-usage-data data)
           :tool-calls (extract-tool-calls data))))
