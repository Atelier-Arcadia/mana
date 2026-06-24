(ns mana.tools
  (:require [clojure.java.io :as io]))

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
  "Request input from the user when you're done working and need instructions for how to proceed or have a question.")
(defn request-input [_args]
  (print "prompt>")
  (flush)
  (str "The user prompted: " (read-line)))
