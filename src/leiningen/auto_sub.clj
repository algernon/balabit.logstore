(ns leiningen.auto-sub
  (:require [robert.hooke :as hooke]
            [leiningen.core.main]
            [leiningen.sub :as sub]))

(defn- with-subs
  "Substitute the :auto-sub submodules into the main project itself."

  [project]

  (assoc project :sub (get-in project [:auto-sub :sub])))

(defn- apply-sub-tasks
  "Run a given task in submodules only, but only if the project has
  settings in :auto-sub."

  [_ [task project args]]

  (apply sub/sub project task args))

(def ^:private mode-map
  {:all [apply-sub-tasks apply]
   :sub-only [apply-sub-tasks]
   :self-only [apply]})

(defn- maybe-sub
  "Run a given task either in sub-projects, or as-is, or both,
  depending on the settings in :auto-sub."

  [f task project args]

  (let [settings (get-in project [:auto-sub (:name project)] nil)
        todo (get mode-map (get settings (keyword task) :self-only))]
    (doall (map #(% f [task (with-subs project) args]) todo))))

(defn activate
  "Activate the task hooks."

  []

  (hooke/add-hook #'leiningen.core.main/apply-task
                  maybe-sub))

(defn auto-sub
  "Hooks into all tasks, and runs them in sub-projects if need be.

  By default, tasks will be passed through as-is, but if the project
  contains an :auto-sub key, settings will be looked up from
  there. The key needs to contain a :sub subkey, which is a vector of
  sub-projects, that will be coerced into the project itself, when a
  task is to be run in sub-projects.

  Other than that, one can set which tasks will be run in sub-projects
  by adding another key into the :auto-sub setting: a project name,
  where the value is a map of task-mode pairs, where tasks are
  keywords, and the values are one of the following: :all, :sub-only,
  or :self-only.

  An example :auto-sub setting may look like this:

      :auto-sub {\"main-project\" {:clean :all
                                   :install :sub-only
                                   :test :sub-only
                                   :compile :sub-only
                                   :deploy :sub-only}
                 :sub [\"sub-1\" \"sub-2\"]}"

  [& _])
