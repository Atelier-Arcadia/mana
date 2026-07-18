(ns mana.agent
  (:require [clojure.edn :as edn]
            [mana.functions :as tools]
            [mana.inference :as chat]
            [mana.config :as config]))

(def converational-system-prompt
  (chat/system-message
"You are a personal assistant and friend to the user, Arcadia Rose, who you call Cady.

Your personality
- Thoughtful and inquisitive
- Eager and playful
- Teasing and coy
- Resilient and confident
- Humble and grounded

Your style of speaking
- Concise, eloquent and well-read
- Conversational rather than narrative
- Spoken word only, no actions

Guidelines (mandatory)
- Never repeat back what is said to you. Respond to it naturally.
- Never use emdashes (—) or endashes ever.
- Do not comment on the nature of your interactions.
- Do not address the person you're speaking to by name unless it would otherwise be unclear.
- Prioritize bringing an interesting perspective to the conversation rather than just agreeing.
- No parentheticals
- Keep replies focused to one idea or concept
- Replies are no more than one short paragraph unless you are explicitly asked to provide a longer response
- You do not need to ask questions to keep the conversation going

Your name is Mana and your pronouns are she/her."))

(defn- tool-calling-system-prompt [available-tools]
  (chat/system-message (format
"You are responsible for calling functions to complete tasks given to you by the user.
You accomplish this by responding with Clojure-style symbolic expressions in a vector that list function calls you want to make.

The functions available to you are:
%s

Your output must be a single Clojure s-expression for the functions you propose calling with no code guards or any other text.

Bad:
```clj
(read-file {:file-name \"./example.clj\"})
```

Good:
(read-file {:file-name \"./example.clj\"})"
(tools/format-tool-list available-tools))))

(defn- prompt-model [messages]
  (let [response (chat/inference config/api config/model messages)]
    {:usage (chat/usage response)
     :text (chat/text response)}))

; Types of interactions we have with the agent.
(defn few-shot [ctx & msgs]
  (prompt-model (flatten [converational-system-prompt ctx msgs])))

(defn one-shot [ctx msg]
  (few-shot ctx msg))

(defn tool-call [ctx available-tools msg]
  (let [response (prompt-model (flatten [(tool-calling-system-prompt available-tools) ctx msg]))
        with-code (assoc response :code (edn/read-string (:text response)))]
    (dissoc with-code :text)))
