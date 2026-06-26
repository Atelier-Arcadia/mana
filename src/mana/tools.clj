(ns mana.tools
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [clj-http.client :as http]))

(alias 'str 'clojure.string)


(def display
  {:name "display"
   :description "Print a message for the user to see. This is the only way to convey a response to the user.
The array of arguments will be merged into a single string to display to the user."
   :implementation (fn [{message "message"}]
                     (println message)
                     "Your message was successfully displayed to the user.")
   :schema {:type "object"
            :properties {:message {:type "string"
                                   :description "The message to display."}}}})

; TODO - Need to be able to read and write at offsets.
(def read-file
  {:name "read-file"
   :description "Read an entire file from a specified path.
Only the first argument in the array will be read. It must be a path to a file relative to the working directory."
   :implementation (fn [{file-path "file_path"}]
                     (slurp file-path))
   :schema {:type "object"
            :properties {:file_path {:type "string"
                                     :description "The path to the file to read."}}}})

(def list-files
  {:name "list-files"
   :description "List the files and directories in a directory.
Only the first argument in the array will be read. It must be a path to a directory relative to the working directory."
   :implementation (fn [{path "path"}]
                     (str/join ", " (seq (.list (io/file path)))))
   :schema {:type "object"
            :properties {:path {:type "string"
                                :description "The path to the directory to list from."}}}})

(def request-input
  {:name "request-input"
   :description "Request input from the user when you're done working and need instructions for how to proceed or have a question.
The first argument will be displayed to the user in an interactive input field. It must be no more than one short sentence."
   :implementation (fn [{prompt "prompt"}]
                     (print (str prompt " > "))
                     (flush)
                     (str "The user responded: " (read-line)))
   :schema {:type "object"
            :properties {:prompt {:type "string"
                                  :description "A prompt to display to the user for them to respond to."}}}})

(def ollama-web-search-url "https://ollama.com/api/web_search")
(defn create-web-search [api-key]
  {:name "web-search"
   :description "Performs a web search for a single query and returns relevant results. Returns a data structure containing:
The first argument is the term to search for.
results (array): array of search result objects, each containing:
    title (string): the title of the web page
    url (string): the URL of the web page
    content (string): relevant content snippet from the web page"
   :implementation (fn [query "query"]
                     (let [body (json/generate-string {:query query :max_results 50}) ; TODO - Revisit the limit
                           req {:accept :json
                                :content-type :json
                                :socket-timeout 60000
                                :connection-timeout 60000
                                :headers {"Authorization" (str "Bearer " api-key)}
                                :body body}
                           res (http/post ollama-web-search-url req)]
                       (:body res)))}
  :schema {:type "object"
           :properties {:query {:type "string"
                                :description "A query to search the web for."}}})

(def ollama-web-fetch-url "https://ollama.com/api/web_fetch")
(defn create-web-fetch [api-key]
  {:name "web-fetch"
   :description "Fetches a single web page by URL and returns its content.
The first argument is the URL of the site to fetch.
Returns a data structure containing:
title (string): the title of the web page
content (string): the main content of the web page
links (array): array of links found on the page"
   :implementation (fn [url "url"]
                     (let [body (json/generate-string {:url url})
                           req {:accept :json
                                :content-type :json
                                :socket-timeout 60000
                                :connection-timeout 60000
                                :headers {"Authorization" (str "Bearer " api-key)}
                                :body body}
                           res (http/post ollama-web-fetch-url req)]
                       (:body res)))}
  :schema {:type "object"
           :properties {:url {:type "string"
                              :description "A URL to fetch from the web."}}})
