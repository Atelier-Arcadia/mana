(ns mana.functions
  (:require [clojure.java.io :as io]))


(def report-finished
  {:name "report-finished"
   :description "Terminate agent loop with a final response to your assigned task."
   :schema [{:name :message :type "string" :description "The final message to report to the user."}]
   :execute identity})


(defn- do-read-file [{fn :file-name}]
  (slurp fn))

(def read-file
  {:name "read-file"
   :description "Read the entire contents of a file."
   :schema [{:name :file-name :type "string" :description "The path to the file to read, relative to the working directory."}]
   :execute do-read-file})


(def hide-from-agent
  [#".*~$"
   #".*#.*\..*#$"
   #".*/?\.env(\.local)?$"
   #".*/?secrets/?$"
   #".*/?secrets/.*$"])

(defn- hide? [file-path]
  (some #(re-matches % file-path) hide-from-agent))

(defn- filter-hidden [ls]
  (filter #(not (hide? %)) ls))

(defn- do-list-directory [{dir :directory}]
  (let [contents (.listFiles (io/file dir))
        files (filter #(.isFile %) contents)
        dirs (filter #(.isDirectory %) contents)]
    {:files       (filter-hidden (map str files))
     :directories (filter-hidden (map str dirs))}))

(def list-directory
  {:name "list-directory"
   :description "List the contents of a directory."
   :schema [{:name :directory :type "string" :description "Path to the directory to list the contents of."}]
   :execute do-list-directory})


(defn- format-arg [{ n :name t :type d :description }]
  (format "    - %s - %s - %s" n t d))

(defn format-tool [{ n :name d :description s :schema }]
  (let [schema-fmt (clojure.string/join "\n" (map format-arg s))]
    (format "- %s - %s\n  schema:\n%s" n d schema-fmt)))

(defn format-tool-list [tools]
  (clojure.string/join "\n" (map format-tool tools)))

(defn dispatch [allowed-tools fn-call]
  (let [tool-name (name (first fn-call))
        tool-args (second fn-call)
        tool      (some #(when (= (:name %) tool-name) %) allowed-tools)]
    (if tool
      {:success true :result ((:execute tool) tool-args)}
      {:success false :reason (format "Tool not allowed: %s" tool-name)})))
