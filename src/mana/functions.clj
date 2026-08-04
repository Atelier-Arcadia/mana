(ns mana.functions
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))


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

(defn- retrieve-single-memory [id]
  (let [error-message (format "No memory with ID \"%s\" found." id)]
    (if (re-matches #"[\w-]*\w+" id)
      (try (edn/read-string (slurp (format "memories/%s.edn" id)))
           (catch Exception e error-message))
      error-message)))

(defn- retrieve-tagged-memories [tags]
  (let [tag-set     (set tags)
        {fs :files} (do-list-directory {:directory "memories/"})
        memories    (map (comp edn/read-string slurp) fs)
        matcher     #(< 0 (count (clojure.set/intersection tag-set (:tags %))))]
    (filter matcher memories)))

(defn- list-all-memories []
  (let [{fs :files} (do-list-directory {:directory "memories/"})
        index-fn    (comp #(select-keys % [:id :brief]) edn/read-string slurp)]
    (reduce conj [] (map index-fn fs))))

(defn- do-remember [{id :id tags :tags}]
  (cond id    (retrieve-single-memory id)
        tags  (retrieve-tagged-memories tags)
        :else (list-all-memories)))

(def recall
  {:name "recall"
   :description "Retrieve memories containing useful context from past activity."
   :schema [{:name :id :optional :true :type "string" :description "When an id is provided, only the memory with that id will be retrieved. Tags are ignored when id is provided."}
            {:name :tags :optional true :type "list of clojure keywords" :description "When no tags are provided, list all memories. Then, when tags are provided, retrieve memories containing those tags."}]
   :execute do-remember})


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
