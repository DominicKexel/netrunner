(ns game.core.change-vals
  (:require
    [game.core.agendas :refer [update-all-agenda-points]]
    [game.core.effects :refer [register-floating-effect]]
    [game.core.events :refer [trigger-event]]
    [game.core.gaining :refer [available-mu base-mod-size deduct free-mu gain]]
    [game.core.say :refer [system-msg]]
    [game.macros :refer [req]]))

(defn- change-msg
  "Send a system message indicating the property change"
  [state side kw new-val delta]
  (let [key (name kw)]
    (system-msg state side
                (str "sets " (.replace key "-" " ") " to " new-val
                     " (" (if (pos? delta) (str "+" delta) delta) ")"))))

(defn- change-map
  "Change a player's property using the :mod system"
  [state side key delta]
  (gain state side key {:mod delta})
  (change-msg state side key (base-mod-size state side key) delta))

(defn- change-mu
  "Send a system message indicating how mu was changed"
  [state side delta]
  (free-mu state delta)
  (system-msg state side
              (str "sets unused MU to " (available-mu state)
                   " (" (if (pos? delta) (str "+" delta) delta) ")")))

(defn- change-tags
  "Change a player's base tag count"
  [state delta]
  (if (pos? delta)
    (do (gain state :runner :tag delta)
        (trigger-event state :runner :manual-gain-tag delta))
    (do (deduct state :runner [:tag (Math/abs delta)])
        (trigger-event state :runner :manual-lose-tag delta)))
  (system-msg state :runner
              (str "sets Tags to " (get-in @state [:runner :tag :base])
                   " (" (if (pos? delta) (str "+" delta) delta) ")")))

(defn- change-bad-pub
  "Change a player's base bad pub count"
  [state delta]
  (if (neg? delta)
    (deduct state :corp [:bad-publicity (Math/abs delta)])
    (gain state :corp :bad-publicity delta))
  (system-msg state :corp
              (str "sets Bad Publicity to " (get-in @state [:corp :bad-publicity :base])
                   " (" (if (pos? delta) (str "+" delta) delta) ")")))

(defn- change-agenda-points
  "Change a player's total agenda points. This is done through registering an agenda
  point effect that's only used when tallying total agenda points. Instead of adding or
  removing these effects, we allow for creating as many as needed to properly adjust
  the total."
  [state side delta]
  (register-floating-effect
    state side nil
    ;; This is needed as `req` creates/shadows the existing `side` already in scope.
    (let [user-side side]
      {:type :user-agenda-points
       ;; `target` is either `:corp` or `:runner`
       :req (req (= user-side target))
       :value delta}))
  (update-all-agenda-points state side)
  (system-msg state side
              (str "sets their agenda points to " (get-in @state [side :agenda-point])
                   " (" (if (pos? delta) (str "+" delta) delta) ")")))

(defn- change-generic
  "Change a player's base generic property."
  [state side key delta]
  (if (neg? delta)
    (deduct state side [key (- delta)])
    (swap! state update-in [side key] (partial + delta)))
  (change-msg state side key (get-in @state [side key]) delta))

(defn change
  "Increase/decrease a player's property (clicks, credits, MU, etc.) by delta."
  [state side {:keys [key delta]}]
  (case key
    :memory (change-mu state side delta)
    :hand-size (change-map state side key delta)
    :tag (change-tags state delta)
    :bad-publicity (change-bad-pub state delta)
    :agenda-point (change-agenda-points state side delta)
    ; else
    (change-generic state side key delta)))