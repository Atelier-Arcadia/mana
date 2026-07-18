(ns mana.core
  (:gen-class)
  (:require [mana.agent :as mana]
            [mana.inference :as chat]
            [mana.functions :as tools :refer [dispatch]]))

(def context (atom []))
(def total-cost (atom {:input-tokens 0 :output-tokens 0}))

(def allowed-tools [tools/read-file])
(def example (chat/user-message "Read the content of ./src/mana/functions.clj"))

(def res (mana/tool-call @context allowed-tools example))

(dispatch [tools/read-file] (:code res))
