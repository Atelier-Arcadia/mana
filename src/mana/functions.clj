(ns mana.functions)

(defn- do-read-file [{fn :file-name}]
  (slurp fn))

(def read-file
  {:name "read-file"
   :description "Read the entire contents of a file."
   :schema [{:name :file-name :type "string" :description "The path to the file to read, relative to the working directory."}]
   :execute do-read-file})


(defn- format-arg [{ n :name t :type d :description }]
  (format "%s - %s - %s" n t d))

(defn format-tool [{ n :name d :description s :schema }]
  (let [schema-fmt (clojure.string/join (map format-arg s) "\n    ")]
    (format "- %s - %s\n  schema:\n%s" n d schema-fmt)))

(defn format-tool-list [tools]
  (map format-tool tools))

(defn dispatch [allowed-tools fn-call]
  (let [tool-name (name (first fn-call))
        tool-args (second fn-call)
        tool      (some #(when (= (:name %) tool-name) %) allowed-tools)]
    (if tool
      {:success true :result ((:execute tool) tool-args)}
      {:success false :reason (format "Tool not allowed: %s" tool-name)})))
