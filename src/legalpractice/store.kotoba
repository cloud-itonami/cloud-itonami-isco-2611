(ns legalpractice.store
  "SSoT for the ISCO-08 2611 independent legal practice actor
  (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors section;
  README's 'Robotics premise' — a legal document intake, scanning and
  binding robot performs case-file preparation, exhibit binding and
  physical filing-room organization under this advisor/governor pair,
  which never dispatches hardware itself and never files or submits
  to a court/registry). Modeled on cloud-itonami-isco-4311's
  bookkeeping.store.

  Domain:

    client — a registered individual/organization (:client-id, :name)
    matter — a registered legal matter {:matter-id :client-id :name
             :max-billable-hours number :conflict-check-cleared?
             boolean}. `:max-billable-hours` is the registered
             engagement-scope ceiling a proposed task's billable hours
             must not exceed — billing beyond the registered scope is
             scope creep, not diligence. `:conflict-check-cleared?`
             records whether a conflict-of-interest check has cleared
             for this matter — preparing work product for a matter
             without a cleared conflict check is an ethics violation,
             not efficient service.
    record — a committed operating record (prepared work product) —
             written ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (matter [s matter-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-matter! [s m])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (matter [_ matter-id] (get-in @a [:matters matter-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-matter! [s m]
    (swap! a assoc-in [:matters (:matter-id m)] m) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :matters {} :records [] :ledger []}
                                   seed)))))
