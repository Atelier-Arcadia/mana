(ns mana.tasks
  (:require [mana.tools :as tools]))

(defn stop [& conditions]
  (fn [history]
    (reduce (fn ([a b] (or a b)))
            (map (fn [condition] (condition history))
                 conditions))))

(defn tool-call? [tool]
  (fn [message]
    (and (= (:role message) "assistant")
         (:function_call message)
         (= (get-in message [:function_call :name])
            (:name tool)))))

(defn max-turns [x]
  #(and (>= (count %) x)
        (str "Exceeded max turns: " x)))

(defn tool-called? [tool]
  #(and (some (tool-call? tool) %)
        (str "Called tool " (:name tool))))

(defn calls-exceed? [tool limit]
  (fn [history]
    (and (->> history
              (filter (tool-call? tool))
              (count)
              (<= limit))
         (str "Exceeded max calls to " (:name tool) ": " limit))))

(defn research [{key :ollama-api-key query :query limit :search-limit}]
  (let [web-search (tools/create-web-search key)
        web-fetch (tools/create-web-fetch key)]
    {:done? (stop (max-turns 15)
                  (tool-called? tools/display)
                  (calls-exceed? web-search limit)
                  (calls-exceed? web-fetch limit))
     :tools [tools/display
             tools/read-file
             web-search
             web-fetch]
     :initial-prompt (str "Your task is to perform research to gather information to display to the user.
Your workflow:
1. At most 5 searches.
2. Synthesize your findings.
3. Write your response with the display tool.

If you encounter errors such as status code 429 or 400 responses from your searches, stop immediately and report the failure instead.

Use the web-search tool to identify sources and then web-fetch tool to obtain detailed information.

Answer the user's query:" query)}))
