(ns mana.core
  (:gen-class)
  (:require [clojure.edn :as edn]
            [mana.inference :as chat]
            [mana.functions :as tools :refer [dispatch]]))

(def api "http://localhost:3000/v1/responses")
(def model "mistralai/devstral-small-2-2512")
(def system-prompt
  (format "You are responsible for calling functions to complete tasks given to you by the user.
You accomplish this by responding with Clojure-style symbolic expressions in a vector that list function calls you want to make.

The functions available to you are:
%s

Your output must be a single Clojure s-expression for the functions you propose calling with no code guards or any other text.

Bad:
```clj
(read-file {:file-name \"./example.clj\"})
```

Good:
(read-file {:file-name \"./example.clj\"})" (tools/format-tool-list [tools/read-file])))


(def example [(chat/system-message system-prompt)
              (chat/user-message "Read the content of ./src/mana/functions.clj")])

(def res (chat/inference api model example))

(def code (edn/read-string (chat/text res)))

(dispatch [tools/read-file] code)

(defn -main [& args]
  (dispatch (edn/read-string (chat/text res))))
