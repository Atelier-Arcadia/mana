(ns mana.agent
  (:require [cheshire.core :as json]
            [mana.infer :as infer]
            [mana.chats :as chats]))

(def tool-calling-system-message
  (chats/system-message "You are an orchestrator of tool calls that utilizes the tools available to you to solve the tasks the user assigns you.
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
"))

(def conversational-system-message
  (chats/user-message "Respond to the user's message: "))

(defn- agent-loop [history generation stop?]
  (if (stop? history)
    (do (println "terminating. reason: " (stop? history))
        history)
    (recur (into history (generation history)) generation stop?)))

(defn- find-tool [available-tools name]
  (some #(when (= (:name %) name) %) available-tools))

(defn- call-tool [history available-tools {name :name args :arguments}]
  (if-let [tool (find-tool available-tools name)]
    (if (nil? (:guard tool))
      (json/generate-string ((:implementation tool) args))
      (let [guard ((:guard tool) args history)]
        (cond (nil? guard) (json/generate-string ((:implementation tool) args))
              (contains? :steer guard) (:steer guard)
              :else "Tool call rejected by guardrail.")))))

(defn- call-tools [cfg task history]
  (let [tools (:tools task)
        response (infer/complete cfg tools history)
        tool-calls (:tool-calls response)
        results (map (partial call-tool history tools) tool-calls)]
    (interleave (map chats/tool-call-message tool-calls)
                (map chats/tool-result-message results))))

; No compaction at this level. Tasks are defined to be completed within a single conversation.
(defn tool-calling [cfg ctx task]
  (let [history (into ctx [tool-calling-system-message (chats/user-message (:prompt task))])]
    (agent-loop history (partial call-tools cfg task) (:done? task))))

(defn conversational [cfg ctx msg]
  (let [history (flatten [ctx conversational-system-message msg])
        response (infer/complete cfg [] history)]
    (chats/assistant-message (:text response))))
