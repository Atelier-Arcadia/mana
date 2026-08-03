(ns mana.tasks.library
  (:require [mana.functions :as fx]
            [mana.tasks.task :as tx])
  (:import [java.nio.file Paths]))


(defn- tool-call? [{tool-name :name} code]
  (= tool-name (name (first code))))

(defn- arguments [code]
  (second code))

(defn- child-path? [parent child]
  (let [parent-path (.normalize (Paths/get parent (into-array String [])))
        child-path  (.normalize (Paths/get child (into-array String [])))]
    (.startsWith child-path parent-path)))

(defn explore-prompt [user-prompt dir]
  (format "Explore the filesystem to develop an understanding of its contents.
Your goal is to build up enough information based on facts found in the files you read to address the user's request.
You are done when you have clear information that allows you to respond to the user's request grounded in factual information.

Your search is constrained to the %s directory and its children.

Request from user: %s" dir user-prompt))

(defn explore-updater [context state tool-call]
  (let [{ fr :files-read dir :directory max :max-reads } state
        finished?       (tool-call? fx/report-finished tool-call)
        read-file?      (tool-call? fx/read-file tool-call)
        file-to-read    (get (arguments tool-call) :file-name)
        has-been-read?  (and read-file? (contains? fr file-to-read))
        outside-dir?    (and read-file? (not (child-path? dir file-to-read)))
        limit-exceeded? (>= (count fr) max)]
    (cond finished?       (tx/complete state)
          limit-exceeded? (tx/error :limit-exceeded)
          outside-dir?    (tx/guide (format "You are only allowed to read files within the %s directory" dir))
          has-been-read?  (tx/guide "You have already read this file.")
          read-file?      (tx/allow (update state :files-read conj file-to-read))
          :else           (tx/allow state))))

(defn explore [{ goal :goal dir :directory max :max-reads }]
  {:id :explore
   :prompt (explore-prompt goal dir)
   :tools [fx/report-finished fx/read-file fx/list-directory]
   :init (fn [] {:files-read #{}
                 :directory dir
                 :max-reads max})
   :update explore-updater})
