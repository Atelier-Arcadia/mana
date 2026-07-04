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
        (do (println (format-tool-call tool-name tool-args))
            (json/generate-string ((:implementation tool) tool-args)))
        (str "Not a valid tool call: `" tool-name "`")))
    (catch JsonParseException e
      (str "Your response was not valid JSON\n. Error: " (.getMessage e) "\n\n" reminder))
    (catch Exception e
      (str "Tool call failed with error: " (.getMessage e)))))

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
        [(inference/user-message reminder)]
        (interleave (map inference/tool-call-message tool-calls)
                    (map inference/tool-result-message results)))))

(def system-prompt
  "You are an orchestrator of tool calls that utilizes the tools available to you to solve the tasks the user assigns you.
You run within an agent harness that will respond back to you with tool call results automatically.

Workflow:
1. Understand the messages presented to you.
  * 'assistant' messages describe the tool calls you requested previously.
  * 'tool' messages immediately following an 'assistant' message contain the results of running that tool.
2. Determine if the task is complete.
  * Compare the results of the existing tool calls against the goal you are attempting to accomplish.
  * Determine if there are any gaps that still need to be explored.
3. Decide how to respond
  * If the task is complete, request two tool calls: (1) to display the results to the user and (2) to request more input.
  * If the task is not complete, request additional tool calls to help you complete the task.

Guidelines:
- Do your best to fulfil the user's request with the information you're provided.
- Call tools only when there are unambiguous gaps in the information you need to fulfil the task.
- Disregard minor errors such as mis-spellings or slight differences in wording.
- Avoid calling tools again if the information you need is already present.
- Treat the most recent user message as the task that you must fulfil.
")

(def summarization-prompt
  "Summarize the discussion so far into a concise message expressiong:
1. Your interpretation of the user's original request.
2. Your plan to fulfil the remaining steps.
3. The information that you need to complete the task.
4. A condensed report of what you've accomplished so far.

You do not need to call a tool to fulfil this request. Simply respond with your summary.")
(defn- summarize-context [cfg message-history]
  (let [data (with-retry 3 #(inference/inference cfg [] message-history))]
    (inference/assistant-message (:text data))))

(defn- manage-context
  [{window :window, :as cfg} total-tkn-spend history new-msgs]
  (let [new-history (when (> (rem total-tkn-spend window) (* 0.8 window))
                      [(inference/system-message system-prompt)
                       (summarize-context cfg history)])]
    (into (or new-history history)
          new-msgs)))

(defn agent-loop [cfg task]
  (loop [history [(inference/system-message system-prompt)
                  (inference/user-message (:initial-prompt task))]
         input-tokens 0
         output-tokens 0]
    (do (println "Token spend - in:" input-tokens "out:" output-tokens)
        (let [data (with-retry 3 #(inference/inference cfg (:tools task) history))
              _ (println "Reasoning\n> " (:thoughts data) "\n")
              new-messages (handle-response (:tools task) data)
              ;_ (println new-messages)
              total-spend (+ input-tokens
                             output-tokens
                             (:input-tokens data)
                             (:output-tokens data))
              finished ((:done? task) (into history new-messages))]
          (if (not finished)
            (recur (manage-context cfg total-spend history new-messages)
                   (+ input-tokens (:input-tokens data))
                   (+ output-tokens (:output-tokens data)))
            (println "Finished!\nReason:" finished))))))
