(ns mana.tasks
  (:require [mana.tools :as tools]))

(defn stop-when [& conditions]
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
        (format "Exceeded max turns: %d" x)))

(defn tool-called? [tool]
  #(and (some (tool-call? tool) %)
        (format "Called tool %s" (:name tool))))

(defn calls-exceed? [tool limit]
  (fn [history]
    (let [times-called (count (filter (tool-call? tool) history))])
    (and (->> history
              (filter (tool-call? tool))
              (count)
              (<= limit))
         (format "Exceeded max calls to %s: %d" (:name tool) limit))))

(def research-prompt-fmt "Your task is to perform research to gather information to display to the user.
Your workflow:
1. At most %d searches.
2. Synthesize your findings.
3. Write your response with the display tool.

If you encounter errors such as status code 429 or 400 responses from your searches, stop immediately and report the failure instead.

Use the web-search tool to identify sources and then web-fetch tool to obtain detailed information.

Answer the user's query: %s")

(defn research
  [{key :ollama-api-key query :query turns :max-turns limit :search-limit}]
  (let [web-search (tools/create-web-search key)
        web-fetch (tools/create-web-fetch key)]
    {:prompt (format research-prompt-fmt limit query)
     :tools [tools/display
             web-search
             web-fetch]
     :done? (stop-when (max-turns turns)
                       (tool-called? tools/display)
                       (calls-exceed? web-search limit)
                       (calls-exceed? web-fetch limit))}))

(defn remember-prompt [tags]
  (format "Your task is to search through memory files to identify information about the content requested by the user.

Workflow:
1. Search for each the tags specified by the user in turn.
2. Identify related tags to search for.
3. Search for those additional tags.
4. Summarize your findings.
5. Call the display tool to report the summary.

You are given the following tags:
%s" (->> tags
         (map #(str % "\n"))
         (clojure.string/join "  - " ))))

;; I tend to think about these things in terms of their done-conditions first.
(defn remember [{ turns :max-turns tags :tags }]
  {:done? (stop-when (max-turns turns)
                     (tool-called? tools/display))
   :tools [tools/lookup-memory
           tools/display]
   :prompt (remember-prompt tags)})

(defn condense-prompt [contents]
  (format "Your task is to summarize the information provided to you by the user.

Workflow:
1. Analyze the information presented to you.
2. Identify any commonalities or patterns.
3. Identify any issues or open questions.
4. Display a summary.

Guidelines:
- Summaries should not exceed three or at most four paragraphs.
- Your tone should be direct and clear; avoid any unnecessary adjectives and colorful language.
- Keep your summaries strictly factual.

Contents provided by the user:

%s" (->> contents
         (map #(format "<|contents|>\n%s\n</|contents|>" %))
         (clojure.string/join "\n\n"))))

(defn condense [{ contents :contents turns :max-turns }]
  {:done? (stop-when (max-turns turns)
                     (tool-called? tools/display))
   :tools [tools/display]
   :prompt (condense-prompt contents)})
