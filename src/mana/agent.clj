(ns mana.agent
  (:require [cheshire.core :as json]
            [mana.inference :as inference])
  (:import (com.fasterxml.jackson.core JsonParseException)
           (java.util.concurrent TimeoutException)))

(alias 'str 'clojure.string)

(defn- find-tool [tool-registry name]
  (some #(when (= (:name %) name) %) tool-registry))

(def reminder
  "Reminder: You must always respond with a tool call.")

(defn- format-tool-call [name args]
  (let [fmt-name (str "(" name ")")
        joined-args (str/join "; " args)
        bound (min 49 (count joined-args))
        first-50 (subs joined-args 0 bound)]
    (str/join " " ["[Tool call]" fmt-name first-50])))

(defn- handle-tool-call
  [tool-registry {tool-name :name tool-args :arguments}]
  "Invoke a tool as specified in a JSON-encoded tool call from the agent."
  (try
    (let [tool (find-tool tool-registry tool-name)]
      (if tool
        ((:implementation tool) tool-args)
        (str "Not a valid tool call: `" tool-name "`")))
    (catch JsonParseException e
      (str "Your response was not valid JSON\n. Error: " (.getMessage e) "\n\n" reminder))
    (catch Exception e
      (str "Tool call failed with error: " (.getMessage e)))))

(def system-prompt
  "You are an orchestrator of tool calls that utilizes the tools available to you to solve the tasks the user assigns you.

You run within an agent harness that will respond back to you with tool call results automatically.
Therefore, you must continue calling tools until the task is complete.

When your task is complete, call the 'request-input' tool to return control to the user to provide additional instructions.")

(defn- with-retry [max-attempts f]
  (when (not (zero? max-attempts))
    (try
      (f)
      (catch TimeoutException e
        (with-retry (dec max-attempts) f))
      (catch java.net.SocketTimeoutException e
        (with-retry (dec max-attempts) f)))))

(defn- handle-response [tools response-data]
  (let [tool-calls (:tool-calls response-data)
        results (map (partial handle-tool-call tools) tool-calls)]
    (if (empty? tool-calls)
        (inference/user-message reminder)
        (interleave (map inference/tool-call-message tool-calls)
                    (map inference/tool-result-message results)))))

(defn agent-loop [cfg tools initial-prompt]
  (loop [history [(inference/system-message system-prompt)
                  (inference/user-message initial-prompt)]
         input-tokens 0
         output-tokens 0]
    (do (println "Token spend - in:" input-tokens "out:" output-tokens)
        (let [data (with-retry 3 #(inference/inference cfg tools history))
              new-messages (handle-response tools data)]
          (recur (into history new-messages)
                 (+ input-tokens (get data :input-tokens))
                 (+ output-tokens (get data :output-tokens)))))))
