(ns mana.inference
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(alias 'str 'clojure.string)

(defn- message [role msgs]
  { :role role :content msgs })

(defn- text-content [text]
  { :type "text" :text text })

(defn- extract-text-response [json-data]
  (str/join
   ""
   (map (fn [choice] (get-in choice ["message" "content"]))
        (get-in json-data ["choices"]))))

(defn- extract-usage-data [json-data]
  (let [usage (get json-data "usage")]
    {:input-tokens (get usage "prompt_tokens")
     :output-tokens (get usage "completion_tokens")}))

(def user-message #(message "user" [(text-content %)]))
(def tool-message #(message "tool" [(text-content %)]))
(def assistant-message #(message "assistant" [(text-content %)]))
(def system-message #(message "system" [(text-content %)]))
(def developer-message #(message "developer" [(text-content %)]))

(defn inference
  [{url :url key :api-key model :model} messages]
  "Perform inference/completion with MiniMax-M3. Creates a hash with
* :text
* :input-tokens
* :output-tokens"
  (let [body (json/generate-string {:model model :messages messages})
        req {:accept :json
             :content-type :json
             :socket-timeout 300000
             :connection-timeout 300000
             :headers {"Authorization" (str "Bearer " key)}
             :body body}
        res (http/post url req)
        data (json/parse-string (get res :body))]
    (merge {:text (extract-text-response data)}
           (extract-usage-data data))))
