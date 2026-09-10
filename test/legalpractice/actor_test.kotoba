(ns legalpractice.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [legalpractice.actor :as actor]
            [legalpractice.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Legal"})
    (store/register-matter! st {:matter-id "M-1" :client-id "client-1"
                                :name "matter-042"
                                :max-billable-hours 40
                                :conflict-check-cleared? true})
    st))

(deftest commits-a-within-scope-cleared-preparation
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-document-preparation :stake :low
                 :matter-id "M-1" :billable-hours 20}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-scope-preparation
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-document-preparation :stake :low
                 :matter-id "M-1" :billable-hours 80}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-approves-court-filing-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-court-filing :stake :low
                 :matter-id "M-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
