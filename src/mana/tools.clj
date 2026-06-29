(ns mana.tools
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [clj-http.client :as http]))

(alias 'str 'clojure.string)


(defn- simple-property
  [[prop-name type description]]
  {prop-name {:type type
              :description description}})

(defn- simple-object-schema [required & properties]
  (let [structured-props (map simple-property properties)
        props (reduce into {} structured-props)]
    {:type "object" :properties props, :required required}))

(defn- merge-schemas [s1 s2]
  {:type "object"
   :properties (apply merge (map :properties [s1 s2]))
   :required (apply into (map :required [s1 s2]))})

(defn- spec->call [spec]
  (if (= :optional (first spec))
    (let [[_ param ts desc] spec]
      (simple-object-schema [] [param ts desc]))
    (let [[param ts desc] spec]
      (simple-object-schema [param] [param ts desc]))))

(defn schema [& specs]
  (reduce merge-schemas (map spec->call specs)))

(defn- do-display
  [{message "message"}]
  (println "\n--- AGENT MESSAGE ----------\n" message "\n----------------------------\n")
  "Your message was successfully displayed to the user.")

(def display
  {:name "display"
   :description "Print a message for the user to see. This is the only way to convey a response to the user.
The array of arguments will be merged into a single string to display to the user."
   :schema (schema [:message "string" "The message to display"])
   :implementation do-display})

; TODO - Need to be able to read and write at offsets.
(def read-file
  {:name "read-file"
   :description "Read an entire file from a specified path.
Only the first argument in the array will be read. It must be a path to a file relative to the working directory."
   :schema (schema [:file_path "string" "The path to the file to read."]
                   [:optional :offset "number" "Start reading after skipping 'offset' lines."]
                   [:optional :limit "number" "Stop reading after 'limit' lines."])
   :implementation (fn [{file-path "file_path"}] (slurp file-path))})

(def list-files
  {:name "list-files"
   :description "List the files and directories in a directory.
Only the first argument in the array will be read. It must be a path to a directory relative to the working directory."
   :schema (schema [:path "string" "The path to the directory to list from."])
   :implementation (fn [{path "path"}] (str/join ", " (seq (.list (io/file path)))))})

(defn- do-request-input
  [{prompt "prompt"}]
  (print (str prompt " > "))
  (flush)
  (str "The user responded: " (read-line)))

(def request-input
  {:name "request-input"
   :description "Request input from the user when you're done working and need instructions for how to proceed or have a question.
The first argument will be displayed to the user in an interactive input field. It must be no more than one short sentence."
   :schema (schema [:prompt "string" "A prompt to display to the user for them to respond to."])
   :implementation do-request-input})


(def ollama-web-search-url "https://ollama.com/api/web_search")
(defn- do-ollama-web-search
  [api-key {query "query"}]
  (let [body (json/generate-string {:query query :max_results 10}) ; TODO - Revisit the limit
        req {:accept :json
             :content-type :json
             :socket-timeout 60000
             :connection-timeout 60000
             :headers {"Authorization" (str "Bearer " api-key)}
             :body body}
        res (http/post ollama-web-search-url req)]
    (:body res)))

(defn create-web-search [api-key]
  {:name "web-search"
   :description "Performs a web search for a single query and returns relevant results. Returns a data structure containing:
The first argument is the term to search for.
results (array): array of search result objects, each containing:
    title (string): the title of the web page
    url (string): the URL of the web page
    content (string): relevant content snippet from the web page"
   :schema (schema [:query "string" "A query to search the web for."])
   :implementation (partial do-ollama-web-search api-key)})

(def ollama-web-fetch-url "https://ollama.com/api/web_fetch")
(defn- do-ollama-web-fetch
  [api-key {url :url}]
  (let [body (json/generate-string {:url url})
        req {:accept :json
             :content-type :json
             :socket-timeout 60000
             :connection-timeout 60000
             :headers {"Authorization" (str "Bearer " api-key)}
             :body body}
        res (http/post ollama-web-fetch-url req)]
    (:body res)))

(defn create-web-fetch [api-key]
  {:name "web-fetch"
   :description "Fetches a single web page by URL and returns its content.
The first argument is the URL of the site to fetch.
Returns a data structure containing:
title (string): the title of the web page
content (string): the main content of the web page
links (array): array of links found on the page"
   :schema (schema [:url "string" "A URL to fetch from the web."])
   :implementation (partial do-ollama-web-fetch api-key)})
