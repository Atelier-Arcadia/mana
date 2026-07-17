(ns mana.infer
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(alias 'str 'clojure.string)

(defn- extract-text-response [json-data]
  (let [choices (get json-data "choices")
        content (map #(get-in % ["message" "content"]) choices)]
    (str/join "" content)))

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

; TODO - Subtract usage::completion_tokens_details::reasoning_tokens from completion_tokens since we don't reflect them back.
(defn- extract-usage-data [json-data]
  (let [usage (get json-data "usage")]
    {:input-tokens (get usage "prompt_tokens")
     :output-tokens (get usage "completion_tokens")}))

(defn- extract-reasoning-content [json-data]
  (let [choices (get json-data "choices")]
    (str/join "\n" (map #(get-in % ["message" "reasoning_content"]) choices))))


(defn- tool-call-spec [{name :name desc :description schema :schema}]
  {:type "function"
   :function {:name name
              :description desc
              :parameters schema}})

(defn- simple-property
  [[prop-name type description]]
  {prop-name {:type type
              :description description}})

(defn- simple-object-schema [required & properties]
  (let [structured-props (map simple-property properties)
        props (reduce into {} structured-props)]
    {:type "object" :properties props, :required required}))

(defn- merge-schemas [s1 s2]
  {:type "object"
   :properties (apply merge (map :properties [s1 s2]))
   :required (apply into (map :required [s1 s2]))})

(defn- spec->schema [spec]
  (if (= :optional (first spec))
    (let [[_ param ts desc] spec]
      (simple-object-schema [] [param ts desc]))
    (let [[param ts desc] spec]
      (simple-object-schema [param] [param ts desc]))))

; Sugar
(defn schema [& specs]
  "Specify a tool that the model will be allowed to choose to call."
  (reduce merge-schemas (map spec->schema specs)))

;(defn- with-retry [max-attempts f]
;  (when (not (zero? max-attempts))
;    (try
;      (f)
;      (catch TimeoutException e
;        (with-retry (dec max-attempts) f))
;      (catch java.net.SocketTimeoutException e
;        (with-retry (dec max-attempts) f)))))

;; Designed intentionally to only work with local models.
(defn complete
  [{url :url model :model} tools messages]
  "Perform inference with a configured model via a server using the OpenAI-style chat/completions API format."
  (let [body (json/generate-string
              {:model model
               :messages (filter some? messages)
               :tools (map tool-call-spec tools)}
              {:pretty true})
        req {:accept :json
             :content-type :json
             :socket-timeout 600000
             :connection-timeout 600000
             :body body}
        res (http/post url req)
        data (json/parse-string (:body res))]
    (into (extract-usage-data data)
          {:tool-calls (extract-tool-calls data)
           :text (extract-text-response data)
           :thoughts (extract-reasoning-content data)})))
