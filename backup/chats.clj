(ns mana.chats)

(alias 'str 'clojure.string)

(defn message [role msgs]
  {:role role :content msgs})

(def user-message #(message "user" %))
(def assistant-message #(message "assistant" %))
(def system-message #(message "system" %))
(def tool-result-message #(message "tool" %))
(def developer-message #(message "developer" %))
(defn tool-call-message [{name :name args :arguments}]
  {:role "assistant"
   ; Just to be REALLY explicit about it :)
   :function_call {:name name :arguments args}})

(defn tool-being-called [msg]
  (get-in msg [:function_call :name]))

(defn- has-role? [msg role]
  (= (:role msg) role))

(defn is_a? [sym msg]
  (cond (= sym :user-message) (has-role? msg "user")
        (= sym :assistant-message) (has-role? msg "assistant")
        (= sym :system-message) (has-role? msg "system")
        (= sym :developer-message) (has-role? msg "developer")
        (= sym :tool-result-message) (has-role? msg "tool")
        (and (= sym :tool-call-message)
             (has-role? msg "assistant")) (contains? msg :function_call)
        :else false))
