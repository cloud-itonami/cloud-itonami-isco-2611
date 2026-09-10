(ns legalpractice.governor
  "LegalPracticeGovernor — the independent safety/traceability layer
  named in this repository's README/business-model.md, gating every
  piece of work product an advisor may propose for a matter. The
  governor never dispatches hardware itself and never files or
  submits anything to a court/registry. Modeled on
  cloud-itonami-isco-4311's bookkeeping.governor. Task twist: a
  proposed task's billable hours are an arithmetic ceiling against
  the matter's registered engagement scope, and work product cannot
  be prepared for a matter until its conflict-of-interest check has
  cleared.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance     — the individual/organization must be
                               registered.
    2. no-actuation          — proposal :effect must be :propose (the
                               governor never dispatches hardware and
                               never files/submits to a court/
                               registry; it only gates what the
                               advisor may prepare).
    3. matter basis          — a work-product proposal must cite a
                               REGISTERED matter belonging to this
                               client.
    4. billable-hours ceiling — the proposed billable hours must not
                               exceed the matter's registered
                               `:max-billable-hours` (billing beyond
                               the registered engagement scope is
                               scope creep, not diligence).
    5. conflict-check cleared — the matter must have
                               `:conflict-check-cleared?` true before
                               any work product can be prepared
                               (preparing work product without a
                               cleared conflict check is an ethics
                               violation, not efficient service).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    6. :op :approve-court-filing (no filing or submission to a court/
                               registry without the governor gate).
    7. :op :approve-new-representation (accepting new representation
                               always requires human sign-off).
    8. low confidence (< `confidence-floor`)."
  (:require [governor.core :as gov]
            [legalpractice.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:approve-court-filing
                                     :approve-new-representation})

(defn- hard-violations
  "Rules 1-4 are the fleet-shared provenance checks, taken from
  `kotoba-lang/governor` instead of hand-copied (they were identical in 376
  repositories, and one of those copies had drifted — ADR-2607309100). The
  wording stays this repo's own; only the logic is shared. Rules 5-6 are this
  actor's domain and stay here."
  [{:keys [request proposal]} client-record m]
  (let [{:keys [op billable-hours]} proposal
        prep? (= :approve-document-preparation op)]
    (cond-> (gov/violations
             (gov/missing-subject client-record {:detail "未登録 client"})
             (gov/no-actuation
              proposal
              {:detail "effect は :propose のみ許可（governor は裁判所/登記所への提出を直接実行しない）"})
             (gov/unknown-scope m {:applies? prep?
                                   :detail "未登録 matter への作業準備は不可"})
             (when prep?
               (gov/scope-owner-mismatch m request {:detail "matter が別 client のもの"})))

      (and prep? m (number? billable-hours) (> billable-hours (:max-billable-hours m)))
      (conj {:rule :billable-hours-exceeds-scope
             :detail (str "請求時間 " billable-hours "h > 登録済み受任範囲上限 "
                          (:max-billable-hours m) "h（登録済み範囲を超える請求はスコープクリープであって注意義務ではない）")})

      (and prep? m (not (:conflict-check-cleared? m)))
      (conj {:rule :conflict-check-not-cleared
             :detail "利益相反チェックが完了していない matter への作業準備は倫理違反であって効率的サービスではない"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `legalpractice.store/Store`. Pure — never
  mutates the store, never files or submits to a court/registry."
  [request _context proposal store]
  (let [client-record (store/client store (:client-id request))
        m (some->> (:matter-id proposal) (store/matter store))]
    (gov/verdict
     {:violations (hard-violations {:request request :proposal proposal}
                                   client-record m)
      :confidence (:confidence proposal)
      :confidence-floor confidence-floor
      :escalating-op? (contains? always-escalate-ops (:op proposal))})))
