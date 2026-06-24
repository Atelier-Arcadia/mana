(ns mana.agent
  (:require [cheshire.core :as json]
            [mana.inference :as inference])
  (:import (com.fasterxml.jackson.core JsonParseException)
           (java.util.concurrent TimeoutException)))

(alias 'str 'clojure.string)

(defn- find-tool [tool-registry name]
  (some #(when (= (:name %) name) %) tool-registry))

(def reminder
  "Reminder: You must always respond with JSON to declare a tool call. Plain messages will not be delivered to the user.")

(defn- handle-tool-call
  [tool-registry assistant-message]
  "Invoke a tool as specified in a JSON-encoded tool call from the agent."
  (try
    (let [tool-call-data (json/parse-string assistant-message)
          tool-name (get tool-call-data "tool_name")
          tool-args (get tool-call-data "arguments")
          tool (find-tool tool-registry tool-name)]
      (when tool
        ((:implementation tool) tool-args)))
    (catch JsonParseException e
      (str "Your response was not valid JSON\n. Error: " (.getMessage e) "\n\n" reminder))
    (catch Exception e
      (str "Tool call failed with error: " (.getMessage e)))))

(defn- format-tool-description
  [{name :name desc :description impl :implementation}]
  (str "-`" name "` - " desc))

(defn- format-tool-list [tools]
  (str/join "\n" (map format-tool-description tools)))

(defn- system-prompt [tool-registry]
  (str "You are an orchestrator of tool calls that utilizes the tools available to you to solve the tasks the user assigns you.

You run within an agent harness that will respond back to you with tool call results automatically.
Therefore, you must continue calling tools until the task is complete.

The complete list of tools available to you are:\n"

  (format-tool-list tool-registry)

"You ALWAYS respond with a JSON-formatted response WITHOUT any markdown code block guards.
The JSON you return MUST contain just two keys:
- `tool_name` - A string identifying the tool to call.
- `arguments` - A list of arguments to pass to the tool call.

Do not enclose the JSON you respond with inside of Markdown code block guards.

Bad:
```json
{ \"tool_name\": \"display\", \"arguments\": [\"Hello!\"]}
``

Bad:
Hello!

Good:
{ \"tool_name\": \"display\", \"arguments\": [\"Hello!\"]}

Guidelines:
- If you do not call the 'display' tool, the user will not see your message.
- All of the work you do must be through tool calls. No exceptions.
- When your task is complete, call the 'request-input' tool to return control to the user.
- If you do not return valid JSON, nothing will happen."))


(defn- with-retry [max-attempts f]
  (when (not (zero? max-attempts))
    (try
      (f)
      (catch TimeoutException e
        (with-retry (dec max-attempts) f))
      (catch java.net.SocketTimeoutException e
        (with-retry (dec max-attempts) f)))))

(defn agent-loop [cfg tools initial-prompt]
  (loop [history [(inference/system-message (system-prompt tools))
                  (inference/user-message initial-prompt)]
         input-tokens 0
         output-tokens 0]
    (do (println "Token spend - in:" input-tokens "out:" output-tokens)
        (let [data (with-retry 3 #(inference/inference cfg history))
              response (:text data)
              _ (println (str "Agent response:" response))
              tool-call-result (handle-tool-call tools response)
              _ (println (str "Tool call result:" tool-call-result))]
          (recur (conj history
                       (inference/assistant-message response)
                       (inference/tool-message (json/generate-string tool-call-result)))
                 (+ input-tokens (get data :input-tokens))
                 (+ output-tokens (get data :output-tokens)))))))
