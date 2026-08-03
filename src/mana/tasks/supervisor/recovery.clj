(ns mana.tasks.supervisor.recovery
  (:require [mana.inference :as chat]
            [mana.tasks.supervisor.supervision :as spv]))

(defn- bruteforce-failure-message [{cause :cause state :state}]
  (chat/user-message (format "You were previously given the following task and failed.
The error cause was labeled as: %s and you finished with the following state.
```clojure
%s
```
Please attempt the following task again, correcting for this failure." cause state)))

(defn bruteforce [original-proc result]
  {:task (:task original-proc)
   :on-error bruteforce
   :on-complete (:on-complete original-proc)
   :context (conj (:context original-proc) (bruteforce-failure-message result))})
