(ns legalpractice.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [legalpractice.store :as store]
            [legalpractice.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Legal"})
    (store/register-matter! st {:matter-id "M-1" :client-id "client-1"
                                :name "matter-042"
                                :max-billable-hours 40
                                :conflict-check-cleared? true})
    st))

(defn- prep-op [hours]
  {:op :approve-document-preparation :effect :propose :matter-id "M-1"
   :billable-hours hours :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-scope-and-cleared
  (let [st (fresh-store)
        v (governor/check req {} (prep-op 20) st)]
    (is (:ok? v))))

(deftest ok-at-exact-scope-boundary
  (testing "the billable-hours ceiling is inclusive"
    (let [st (fresh-store)
          v (governor/check req {} (prep-op 40) st)]
      (is (:ok? v)))))

(deftest hard-on-billable-hours-exceeds-scope
  (testing "billing beyond the registered engagement scope is scope creep, not diligence"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (prep-op 80) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :billable-hours-exceeds-scope (:rule %)) (:violations v))))))

(deftest hard-on-conflict-check-not-cleared
  (testing "preparing work product without a cleared conflict check is an ethics violation, not efficient service"
    (let [st (store/mem-store)]
      (store/register-client! st {:client-id "client-1" :name "Kobo Legal"})
      (store/register-matter! st {:matter-id "M-1" :client-id "client-1"
                                  :name "matter-042"
                                  :max-billable-hours 40
                                  :conflict-check-cleared? false})
      (let [v (governor/check req {} (assoc (prep-op 20) :confidence 0.99) st)]
        (is (:hard? v))
        (is (some #(= :conflict-check-not-cleared (:rule %)) (:violations v)))))))

(deftest hard-on-unknown-matter
  (let [st (fresh-store)
        v (governor/check req {} (assoc (prep-op 20) :matter-id "M-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-matter (:rule %)) (:violations v)))))

(deftest hard-on-foreign-matter
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (prep-op 20) st)]
      (is (:hard? v))
      (is (some #(= :matter-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (prep-op 20) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (prep-op 20) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest always-escalates-court-filing-even-at-high-confidence
  (testing "no filing or submission to a court/registry without the governor gate"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-court-filing :effect :propose
                                    :matter-id "M-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-new-representation-even-at-high-confidence
  (testing "accepting new representation always requires human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-new-representation :effect :propose
                                    :matter-id "M-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (prep-op 20) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
