(ns mana.tools
  (:require [clojure.java.io :as io]
            [cheshire.core :as json]
            [clj-http.client :as http]))

(alias 'str 'clojure.string)

(def display-description
  "Print a message for the user to see. This is the only way to convey a response to the user.
The array of arguments will be merged into a single string to display to the user.")
(defn display
  [args]
  "Print a message to the user."
  (println (str/join "" args))
  "Your message was successfully displayed to the user.")

; TODO - Need to be able to read and write at offsets.
(def read-file-description
  "Read an entire file from a specified path.
Only the first argument in the array will be read. It must be a path to a file relative to the working directory.")
(defn read-file
  [args] (slurp (first args)))

(def list-files-description
  "List the files and directories in a directory.
Only the first argument in the array will be read. It must be a path to a directory relative to the working directory.")
(defn list-files [args]
  (str/join "\n" (seq (.list (io/file (first args))))))

(def request-input-description
  "Request input from the user when you're done working and need instructions for how to proceed or have a question.
The first argument will be displayed to the user in an interactive input field. It must be no more than one short sentence.")
(defn request-input [args]
  (print (str (first args) "> "))
  (flush)
  (str "The user wrote: " (read-line)))

(def ollama-web-search-url "https://ollama.com/api/web_search")
(def web-search-description
  "Performs a web search for a single query and returns relevant results. Returns a data structure containing:
The first argument is the term to search for.
results (array): array of search result objects, each containing:
    title (string): the title of the web page
    url (string): the URL of the web page
    content (string): relevant content snippet from the web page")
(defn create-web-search [api-key]
  "Creates a function for the model to call that closes over an Ollama API key."
  (fn [args]
    (let [body (json/generate-string {:query (first args) :max_results 50}) ; TODO - Revisit the limit
          req {:accept :json
               :content-type :json
               :socket-timeout 60000
               :connection-timeout 60000
               :headers {"Authorization" (str "Bearer " api-key)}
               :body body}
          res (http/post ollama-web-search-url req)]
      (:body res))))

(def ollama-web-fetch-url "https://ollama.com/api/web_fetch")
(def web-fetch-description
  "Fetches a single web page by URL and returns its content.
The first argument is the URL of the site to fetch.
Returns a data structure containing:
title (string): the title of the web page
content (string): the main content of the web page
links (array): array of links found on the page
")
(defn create-web-fetch [api-key]
  "Creates a function for the model to call that closes over an Ollama API Key."
  (fn [args]
    (let [body (json/generate-string {:url (first args)})
          req {:accept :json
               :content-type :json
               :socket-timeout 60000
               :connection-timeout 60000
               :headers {"Authorization" (str "Bearer " api-key)}
               :body body}
          res (http/post ollama-web-fetch-url req)]
      (:body res))))
