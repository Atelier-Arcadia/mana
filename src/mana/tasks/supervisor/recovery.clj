(ns mana.tasks.supervisor.recovery
  (:require [mana.inference :as chat]
            [mana.tasks.supervisor.supervision :as spv]))


(defn- generic-failure-message [{cause :cause state :state}]
  (format "You were previously given the following task and failed.
The error cause was labeled as: %s and you finished with the following state.
```clojure
%s
```
Please attempt the following task again, correcting for this failure." cause state))

(defn- retry-failure-message [times-remaining result]
  (format "%s\nYou are allowed %d more attempts. Prioritize only the remaining work."
          (generic-failure-message result)
          times-remaining))

(defn bruteforce [{t :task h :on-complete} result]
  {:task t
   :on-error bruteforce
   :on-complete h
   :context [(chat/user-message (generic-failure-message result))]})

(defn retry [& {:keys [times]}]
  (fn [{t :task h :on-complete} result]
    {:task t
     :on-error (retry :times (dec times))
     :on-complete h
     :context [(chat/user-message (retry-failure-message (dec times) result))]}))

(defn resume [& {:keys [times]}]
  (fn [{t :task h :on-complete c :context} result]
    {:task t
     :on-error (resume :times (dec times))
     :on-complete h
     :context (conj c (retry-failure-message (dec times) result))}))

(defn reconfigure [generate-new-task manipulate-context]
  (fn [{h :on-complete e :on-error c :context} result]
    {:task (generate-new-task result)
     :on-error e
     :on-complete h
     :context (manipulate-context c)}))

(defn fallback [recovery]
  (fn [{t :task h :on-complete} result]
    {:task t
     :on-error (recovery result)
     :on-complete h
     :context [(chat/user-message (generic-failure-message result))]}))
