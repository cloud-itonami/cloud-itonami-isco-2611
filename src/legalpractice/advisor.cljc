(ns legalpractice.advisor
  "Legal Advisor — the advisor named in this repository's README,
  proposing a legal-practice operation (prepare work product, approve
  a court filing, approve new representation) from a client intake,
  matter scope and conflict check. Swappable mock/llm; the advisor
  ONLY proposes — `legalpractice.governor` checks the billable-hours
  ceiling and conflict-check clearance independently and always
  escalates court-filing and new-representation decisions. Modeled on
  cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-document-preparation|:approve-court-filing|:approve-new-representation
               :effect :propose :matter-id str :billable-hours number
               :stake kw :confidence n :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake matter-id billable-hours] :as request}]
  {:op op
   :effect :propose
   :matter-id matter-id
   :billable-hours billable-hours
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a legal-practice advisor. Given a request, propose an :op,
   the :matter-id and :billable-hours, an honest :confidence and a
   :stake. Never propose billable hours beyond the matter's registered
   engagement scope, or work product for a matter without a cleared
   conflict check — the governor checks both against the registered
   matter record. Court filing and accepting new representation always
   require human sign-off regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
