(ns mana.core
  (:gen-class)
  (:require [mana.inference :as chat]
            [mana.agent :refer [agent say act stop]]
            [mana.functions :as tools :refer [dispatch]]))

(def context (atom []))
(def total-cost (atom {:input-tokens 0 :output-tokens 0}))

(def mana (agent))
(def allowed-tools [tools/read-file tools/list-directory])

(defn !
  ([p] (say mana @context p))
  ([t p] (act mana @context t p)))

(! "Hey mana, good morning! <3")

;(def res (mana/tool-call @context allowed-tools example))

;(dispatch [tools/read-file] (:code res))
