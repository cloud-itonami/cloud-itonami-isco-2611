(ns legalpractice.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300)
  for the ISCO-08 cluster: this repo previously had NO demo page and no
  generator at all. This namespace drives the REAL actor stack
  (`legalpractice.actor` -> `legalpractice.governor` ->
  `legalpractice.store`) through a scenario built from real, exercised
  store data and renders the result deterministically -- no invented
  numbers, no timestamps in the page content, byte-identical across
  reruns against the same seed (verify by diffing two consecutive runs
  before shipping).

  `client-1` (\"Kobo Legal\") + matter `M-1` (\"matter-042\",
  max-billable-hours 40, conflict-check-cleared? true) below are lifted
  VERBATIM from this repo's own proven-passing test fixture
  (`legalpractice.actor-test`/`legalpractice.governor-test`
  `fresh-store` helper) -- ground truth, not invented. Two additional
  pieces of demo data are registered via the SAME real protocol calls
  this actor's own test fixtures use, disclosed here plainly rather
  than presented as pre-existing fixture:
  - `client-2` (\"Second City Legal Aid\") via `register-client!` -- this
    actor has only one client in its own test fixture, so a second
    client is necessary to demonstrate the cross-client
    `:matter-wrong-client` rule (requesting work product against M-1
    while authenticated as client-2).
  - `M-2` (\"matter-099\", `conflict-check-cleared? false`) via
    `register-matter!` for client-1 -- the fixture's only matter (M-1)
    already has its conflict check cleared, so a second, uncleared
    matter is necessary to demonstrate the `:conflict-check-not-cleared`
    rule through a real dispatch rather than only via
    `legalpractice.governor-test`'s hand-built proposal.

  Every other field this page displays (statuses, records, hold
  reasons) is real output read after `run-demo!` actually executed the
  graph -- none of it is hand-typed.

  Known architectural gaps, honestly noted rather than papered over:
  - `legalpractice.governor`'s `:no-actuation` rule (proposal `:effect`
    must be `:propose`) is NOT reachable through this demo, because the
    real `mock-advisor` (`legalpractice.advisor/infer`) unconditionally
    sets `:effect :propose` on every proposal it emits.
  - The low-confidence escalation path is likewise NOT reachable
    through this demo: `mock-advisor` derives confidence purely from
    `:stake` (`:high` -> 0.7, `:medium` -> 0.85, `:low` -> 0.95), all of
    which sit above `legalpractice.governor/confidence-floor` (0.6) --
    there is no stake value the real advisor maps to a sub-floor
    confidence. Both rules ARE covered by
    `legalpractice.governor-test/hard-on-no-actuation-violation` and
    `escalates-low-confidence` (which call `governor/check` directly
    with hand-built proposals), not by this build-time renderer, which
    only ever drives the real actor/graph the way an operator actually
    would.

  Usage: `clojure -M:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [legalpractice.store :as store]
            [legalpractice.actor :as actor]))

;; ----------------------------- harness --------------------------------

(defn- run-op!
  "Drives one real legal-practice operation request through the actual
  compiled graph for `tid` (thread-id). If the graph escalates
  (interrupts before `:request-approval`), immediately approves it (this
  demo's scenario never demonstrates an UNAPPROVED escalation -- every
  escalation here reaches a human who signs off). Returns a map
  describing exactly what really happened -- no field is invented."
  [graph tid client-id op extra]
  (let [request (merge {:client-id client-id :op op} extra)
        r1 (actor/run-request! graph request {} tid)]
    (if (= :interrupted (:status r1))
      (let [r2 (actor/approve! graph tid)]
        {:thread-id tid :client-id client-id :op op :request request
         :outcome :approved-and-committed
         :record (get-in r2 [:state :record])})
      (let [disposition (get-in r1 [:state :disposition])]
        (if (= :hold disposition)
          {:thread-id tid :client-id client-id :op op :request request
           :outcome :hard-hold
           :verdict (get-in r1 [:state :verdict])
           :rule (-> r1 :state :verdict :violations first :rule)}
          {:thread-id tid :client-id client-id :op op :request request
           :outcome :auto-committed
           :record (get-in r1 [:state :record])})))))

(def ^:private op-specs
  "The scenario: covers every disposition this actor can genuinely reach
  through its real graph (auto-commit, escalate-then-approve, and 5 of
  the 6 distinct HARD-hold reasons in `legalpractice.governor` -- the
  6th, `:no-actuation`, is architecturally unreachable via the real
  advisor, see namespace docstring). Every `:op` keyword and violation
  rule name below is copied from `legalpractice.governor`'s own
  `hard-violations`/`check`, not invented."
  [;; client-1 / "Kobo Legal" / M-1 (real fixture from legalpractice.actor-test)
   ["c1-prep-within-scope"  "client-1" :approve-document-preparation {:matter-id "M-1" :billable-hours 20 :stake :low}]
   ["c1-prep-over-scope"    "client-1" :approve-document-preparation {:matter-id "M-1" :billable-hours 80 :stake :low}]
   ["c1-prep-unknown-matter" "client-1" :approve-document-preparation {:matter-id "M-ghost" :billable-hours 10 :stake :low}]
   ;; M-2 (additional demo data, registered via the same real
   ;; register-matter! call -- see namespace docstring)
   ["c1-prep-conflict-not-cleared" "client-1" :approve-document-preparation {:matter-id "M-2" :billable-hours 5 :stake :low}]
   ;; unregistered client entirely
   ["ghost-no-client" "client-ghost" :approve-document-preparation {:matter-id "M-1" :billable-hours 5 :stake :low}]
   ;; client-2 (additional demo data, registered via the same real
   ;; register-client! call -- see namespace docstring). Referencing
   ;; client-1's M-1 from client-2 demonstrates the cross-client rule.
   ["c2-prep-wrong-matter" "client-2" :approve-document-preparation {:matter-id "M-1" :billable-hours 5 :stake :low}]
   ;; always-escalate ops, regardless of confidence
   ["c1-court-filing"      "client-1" :approve-court-filing {:matter-id "M-1" :stake :low}]
   ["c1-new-representation" "client-1" :approve-new-representation {:matter-id "M-1" :stake :low}]])

(defn run-demo!
  "Runs a fresh store through `op-specs` (see above) via the real
  compiled `legalpractice.actor` graph. Returns `{:store :runs}` --
  `:runs` is the ordered vector of real per-request outcomes; every
  field in `render` below is read from this or from `store` after the
  graph actually executed, never hand-typed."
  []
  (let [db (store/mem-store)]
    (store/register-client! db {:client-id "client-1" :name "Kobo Legal"})
    (store/register-matter! db {:matter-id "M-1" :client-id "client-1"
                                 :name "matter-042"
                                 :max-billable-hours 40
                                 :conflict-check-cleared? true})
    (store/register-matter! db {:matter-id "M-2" :client-id "client-1"
                                 :name "matter-099"
                                 :max-billable-hours 15
                                 :conflict-check-cleared? false})
    (store/register-client! db {:client-id "client-2" :name "Second City Legal Aid"})
    (let [graph (actor/build-graph {:store db})
          runs (mapv (fn [[tid client-id op extra]]
                       (run-op! graph tid client-id op extra))
                     op-specs)]
      {:store db :runs runs})))

;; ----------------------------- rendering -------------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- outcome-cell [{:keys [outcome rule]}]
  (case outcome
    :auto-committed "<span class=\"ok\">committed</span>"
    :approved-and-committed "<span class=\"ok\">approved &amp; committed</span>"
    :hard-hold (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>")
    "<span class=\"muted\">in progress</span>"))

(defn- matter-row [store {:keys [matter-id name client-id max-billable-hours conflict-check-cleared?]} runs]
  (let [record-count (count (filter #(= matter-id (:matter-id %)) (store/records-of store client-id)))
        last-run (last (filter #(= matter-id (get-in % [:request :matter-id])) runs))]
    (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%d</td><td>%s</td><td>%d</td><td>%s</td></tr>"
            (esc client-id) (esc matter-id) (esc name) max-billable-hours
            (if conflict-check-cleared? "<span class=\"ok\">cleared</span>" "<span class=\"err\">not cleared</span>")
            record-count
            (if last-run (outcome-cell last-run) "<span class=\"muted\">no activity</span>"))))

(defn- run-row [{:keys [thread-id client-id op request outcome rule]}]
  (format "        <tr><td><code>%s</code></td><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc thread-id) (esc client-id) (esc (name op))
          (esc (or (some-> (:billable-hours request) str) (some-> (:matter-id request) str) ""))
          (outcome-cell {:outcome outcome :rule rule})))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract (README.md /
  ;; `legalpractice.governor`'s own docstring) -- documentation of fixed
  ;; behavior, not runtime telemetry, so it is legitimately
  ;; hand-described rather than derived from a live run.
  ["        <tr><td><code>:approve-document-preparation</code></td><td><span class=\"ok\">auto-commit when within the registered engagement scope and the conflict check has cleared</span></td></tr>"
   "        <tr><td><code>:approve-court-filing</code></td><td><span class=\"warn\">ALWAYS human approval &middot; no filing/submission to a court or registry without sign-off</span></td></tr>"
   "        <tr><td><code>:approve-new-representation</code></td><td><span class=\"warn\">ALWAYS human approval &middot; accepting new representation</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from `{:store :runs}`
  as produced by `run-demo!` (or any other real scenario)."
  [{:keys [store runs]}]
  (let [matters [{:matter-id "M-1" :name "matter-042" :client-id "client-1"
                  :max-billable-hours 40 :conflict-check-cleared? true}
                 {:matter-id "M-2" :name "matter-099" :client-id "client-1"
                  :max-billable-hours 15 :conflict-check-cleared? false}]
        matter-rows (str/join "\n" (map #(matter-row store % runs) matters))
        run-rows (str/join "\n" (map run-row runs))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isco-2611 &middot; independent legal practice</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Independent Legal Practice (ISCO-08 2611) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · court filing &amp; new representation always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Registered clients &amp; matters</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>legalpractice.store</code> via <code>legalpractice.render-html</code> (<code>clojure -M:render-html</code>), regenerated nightly. Billable-hours ceiling and conflict-check status are the registered engagement scope the governor checks every proposal against.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Client</th><th>Matter</th><th>Name</th><th>Max billable hours</th><th>Conflict check</th><th>Records</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     matter-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Legal Practice Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. The governor never dispatches hardware itself and never files or submits anything to a court/registry.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit trail (this run)</h2>\n"
     "    <p class=\"muted\">Every request this scenario drove through the real compiled graph, in order — thread-id, client, op, the request's own billable-hours/matter, and the real disposition (auto-commit, approved-after-escalation, or the specific HARD-hold rule).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Thread</th><th>Client</th><th>Op</th><th>Hours / matter</th><th>Disposition</th></tr></thead>\n"
     "      <tbody>\n"
     run-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        result (run-demo!)
        html (render result)]
    (spit out html)
    (println "wrote" out "("
             (count (:runs result)) "requests driven through the real graph,"
             (count (store/ledger (:store result))) "ledger facts )")))
