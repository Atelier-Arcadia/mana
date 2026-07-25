(ns mana.kernel
  (:require [mana.inference :as chat]
            [clojure.core.async :refer [<! >! >!! <!! chan go-loop thread]]))

(def api "http://localhost:3000/v1/responses")
(def model "mistralai/devstral-small-2-2512")

(defn proc [id handler]
  (let [requests (chan)
        responses (chan)]
    (thread (loop [req (<!! requests)]
              (>!! responses (handler req))
              (recur (<!! requests))))
    {:id id :send requests :recv responses}))

(defn- k? [kind {k :kind}]
  (= k kind))

(defn- say! [{msgs :messages}]
  (chat/converse api model msgs))

(defn- act! [{tools :tools msgs :messages}]
  (chat/tool-call api model tools msgs))

(defn repl []
  (proc :repl
   (fn [msg]
     (cond (k? :stop msg)     nil
           (k? :converse msg) (say! msg)
           (k? :action msg)   (act! msg)))))

(defn send
  ([{to-proc :send} msgs]
   (if (contains? msgs :stop)
     (>!! to-proc {:kind :stop})
     (>!! to-proc {:kind :converse :messages msgs})))
  ([{to-proc :send} tools msgs]
   (>!! to-proc {:kind :action :tools tools :messages msgs})))

(defn receive [{from-proc :recv}]
  (<!! from-proc))
