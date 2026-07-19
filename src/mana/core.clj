(ns mana.core
  (:gen-class)
  (:require [mana.inference :as chat]
            [mana.agent :refer [agent]]
            [mana.functions :as tools]
            [cheshire.core :as json]
            [clojure.core.async :refer [>!! <!!]]))

(def context (atom []))
(def tool-request (atom nil))
(def usage (atom {:input-tokens 0 :output-tokens 0}))

(def mana (agent))
(def allowed-tools [tools/read-file tools/list-directory])


(defn clear! []
  (reset! context []))

(defn stop! []
  (>!! (:send mana) {:stop true}))

(defn say [prompt]
  (>!! (:send mana) {:context @context :message (chat/user-message prompt)})
  (let [{cost :usage text :text} (<!! (:recv mana))]
    (swap! usage #(merge-with + % cost))
    (swap! context into [(chat/user-message prompt) (chat/assistant-message text)])
    (println text)))

(defn act [tools prompt]
  (>!! (:send mana) {:context @context :tools tools :message (chat/user-message prompt)})
  (let [{cost :usage code :code} (<!! (:recv mana))]
    (reset! tool-request {:code code :tools tools})
    (swap! usage #(merge-with + % cost))
    (swap! context into [(chat/user-message prompt) (chat/assistant-message (str code))])
    (println (format "---\nTool call requested:\n%s\n" code))))

(defn y []
  (let [{tools :tools code :code} @tool-request
        result (tools/dispatch tools code)]
    (reset! tool-request nil)
    (swap! context conj (chat/user-message (json/generate-string result)))
    result))

(defn n []
  (reset! tool-request nil)
  (swap! context conj (chat/user-message "Denied tool call")))

(defn !
  ([] (stop!))
  ([p] (say p))
  ([t p] (act t p)))

(defmacro !! [code]
  `(let [fmt# (format "```clojure\n%s\n```" '~code)
         result# ~code]
     (swap! context conj (chat/user-message (format "The user shared code with you:\n\n%s\nResult: %s" fmt# result#)))
     result#))
