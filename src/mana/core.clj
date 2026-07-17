(ns mana.core
  (:gen-class)
  (:require [mana.inference :as chat]))


(defn -main [& args]
  (println "Hello, mana!"))

(def api "http://localhost:3000/v1/responses")
(def model "mistralai/devstral-small-2-2512")

(def example [(chat/user-message "Who was miles davis?")
              (chat/assistant-message "He was a rock-n-roll man")
              (chat/user-message "Answer seriously")])

(def res (chat/inference api model [] [example]))

(chat/text res)
