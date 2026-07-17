(ns mana.agent
  (:require [cheshire.core :as json]
            [mana.infer :as infer]
            [mana.chats :as chats]))

(def tool-calling-system-message
  (chats/system-message (str "You are an orchestrator of tool calls that utilizes the tools available to you to solve the tasks the user assigns you.
You run within an agent harness that will respond back to you with tool call results automatically.

Workflow:
1. Understand the messages presented to you.
  * 'assistant' messages describe the tool calls you requested previously.
  * 'tool' messages immediately following an 'assistant' message contain the results of running that tool.
2. Determine if the task is complete.
  * Compare the results of the existing tool calls against the goal you are attempting to accomplish.
  * Determine if there are any gaps that still need to be explored.
3. Decide how to respond
  * If the task is complete, request the 'respond' tool call to display a message to the user.
  * If the task is not complete, request additional tool calls to help you complete the task.

The current date is: " (.format (java.time.LocalDate/now) (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd"))
"When the user references a time period, you should interpret it relative to the current date.

Disregard minor errors such as mis-spellings or slight differences in wording.")))

(def conversational-system-message
  (chats/user-message "Respond to the user's message: "))

(defn- agent-loop [history generation stop?]
  (loop [msgs history]
    (let [check (stop? msgs)
          {reason :stop} check
          {guidance :steer} check
          _ (println "Got  guidance" guidance)]
      (cond guidance (do (println "Recurring with steering")
                         (recur (into msgs (generation (conj msgs (chats/developer-message guidance))))))
            reason (do (println "Stopping. Reason:" reason)
                       history)
            :else (do (println "Recurring next turn")
                      (recur (into msgs (generation msgs))))))))

(defn- find-tool [available-tools name]
  (some #(when (= (:name %) name) %) available-tools))

(defn- call-tool [history tools tool-call]
  (let [tool-name (:name tool-call)
        tool-args (:arguments tool-call)
        tool (find-tool tools tool-name)
        guard-fn (or (:guard tool) (fn [_t _h] nil))
        guard-result (guard-fn tool history)
        _ (println "Result of guardrail call: " guard-result)]
    (cond (nil? guard-result) (json/generate-string ((:implementation tool) tool-args))
          (contains? :steer guard-result) (:steer guard-result)
          :else "Tool call rejected by guardrail.")))

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
