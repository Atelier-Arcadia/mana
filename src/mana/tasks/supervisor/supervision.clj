(ns mana.tasks.supervisor.supervision
  (:require [mana.tasks.task :as tx]
            [clojure.core.async :as async :refer [>!! <!!]]))

(defn supervisor [model-dispatch]
  (let [<results (async/chan)]
    {:model-dispatch model-dispatch
     :completions <results
     :monitor (async/mix <results)}))

(defn monitor [supervisor { task :task recover :on-error handler :on-complete ctx :context :as process}]
  (let [<result  (tx/run-thread (:model-dispatch supervisor) ctx task)
        <monitor (async/map (fn [result] {:result result :process process}) [<result])]
    (async/admix (:monitor supervisor) <monitor)))

(defn- maybe-retry [supervisor new-process]
  (when new-process
    (monitor supervisor new-process)))

(defn run-thread [supervisor]
  (async/thread
    (loop [{res :result proc :process} (<!! (:completions supervisor))]
      (case (:mode res)
        :complete ((:on-complete proc) (:task proc) res)
        :error    (maybe-retry supervisor ((:on-error proc) proc res)))
      (recur (<!! (:completions supervisor))))))
