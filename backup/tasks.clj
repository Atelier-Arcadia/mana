(ns mana.tasks
  (:require [mana.tools :as tools]
            [mana.chats :as chats]))

(defn- either? [a b]
  (or a b))

(defn- tool-call? [tool]
  #(and (chats/is_a? :tool-call-message %)
        (= (chats/tool-being-called %) (:name tool))))

(defn max-turns [x]
  #(and (>= (count %) x)
        {:stop (format "Exceeded max turns: %d" x)}))

(defn tool-called? [tool]
  #(and (some (tool-call? tool) %)
        {:stop (format "Called tool %s" (:name tool))}))

(defn calls-exceed? [tool limit]
  #(and (->> %
             (filter (tool-call? tool))
             (count)
             (<= limit))
        {:stop (format "Exceeded max calls to %s: %d" (:name tool) limit)}))

(defn- responded-after-sufficient-research? [min-searches]
  (fn [history]
    (let [did-min-research (and ((calls-exceed? tools/web-search min-searches) history)
                                ((calls-exceed? tools/web-fetch min-searches) history))
          called-respond ((tool-called? tools/respond) history)]
      (cond (and did-min-research called-respond) {:stop "Research complete."}
            (did-min-research))

      {:steer "Perform more fetch tool calls for more information."})))

; STOP!
(defn ! [& conditions]
  "Check if any stop-condition has been reached, returning either the stop message or steering message provided."
  (fn [history]
    (->> conditions (map #(% history)) (reduce either?))))


(defn- research-prompt [query]
  (format "Your task is to perform research to gather information to respond to the user.

You must synthesize search terms to query so that you find a diverse but relevant array of results.

Your workflow:
1. Perform multiple searches to identify useful sources of information.
2. Perform web fetches to obtain details from the sources identified.
3. Synthesize the findings into a coherent narrative.
4. Respond to the user with the summary you produce.

Answer the user's query: %s" query))

(defn research [{query :query turns :max-turns limit :search-limit min :minimum-searches}]
  {:prompt (research-prompt query)
   :tools [tools/respond
           tools/web-search
           tools/web-fetch]
   :done? (! (responded-after-sufficient-research? min)
             (max-turns turns)
             (calls-exceed? tools/web-search limit)
             (calls-exceed? tools/web-fetch limit))})

(defn remember-prompt [tags]
  (format "Your task is to search through memory files to identify information about the content requested by the user.

Workflow:
1. Search for each the tags specified by the user in turn.
2. Identify related tags to search for.
3. Search for those additional tags.
4. Summarize your findings.
5. Call the respond tool to report the summary.

You are given the following tags:
%s" (->> tags
         (map #(str % "\n"))
         (clojure.string/join "  - " ))))

;; I tend to think about these things in terms of their done-conditions first.
(defn remember [{ turns :max-turns tags :tags }]
  {:done? (! (max-turns turns)
             (tool-called? tools/respond))
   :tools [tools/lookup-memory
           tools/respond]
   :prompt (remember-prompt tags)})

(defn condense-prompt [contents]
  (format "Your task is to summarize the information provided to you by the user.

Workflow:
1. Analyze the information presented to you.
2. Identify any commonalities or patterns.
3. Identify any issues or open questions.
4. Respond with a summary.

Guidelines:
- Summaries should not exceed three or at most four paragraphs.
- Your tone should be direct and clear; avoid any unnecessary adjectives and colorful language.
- Keep your summaries strictly factual.

Contents provided by the user:

%s" (->> contents
         (map #(format "<|contents|>\n%s\n</|contents|>" %))
         (clojure.string/join "\n\n"))))

(defn condense [{ contents :contents turns :max-turns }]
  {:done? (! (max-turns turns)
             (tool-called? tools/respond))
   :tools [tools/respond]
   :prompt (condense-prompt contents)})
