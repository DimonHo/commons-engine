package com.commonsengine.platform.domain;

import com.commonsengine.platform.geo.GeoPoint;

import java.util.Set;

/**
 * Domain model aggregates for the Commons Engine platform.
 *
 * <p>All types are immutable Java {@code record}s (JDK 21).
 */
public final class Model {

    private Model() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    // ------------------------------------------------------------------
    // Identifiers
    // ------------------------------------------------------------------

    /**
     * Worker identifier — a generated opaque string.
     */
    public record WorkerId(String value) {

        public WorkerId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("WorkerId must not be blank");
            }
        }

        public static WorkerId random() {
            return new WorkerId(java.util.UUID.randomUUID().toString());
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Consumer identifier.
     */
    public record ConsumerId(String value) {

        public ConsumerId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("ConsumerId must not be blank");
            }
        }

        public static ConsumerId random() {
            return new ConsumerId(java.util.UUID.randomUUID().toString());
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Service request identifier.
     */
    public record RequestId(String value) {

        public RequestId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("RequestId must not be blank");
            }
        }

        public static RequestId random() {
            return new RequestId(java.util.UUID.randomUUID().toString());
        }

        @Override
        public String toString() {
            return value;
        }
    }

    // ------------------------------------------------------------------
    // Aggregates
    // ------------------------------------------------------------------

    /**
     * A platform worker (service provider).
     */
    public record Worker(
            WorkerId id,
            String name,
            GeoPoint currentLocation,
            double rating,
            int activeOrderCount,
            Set<ServiceType> preferredServiceTypes
    ) {

        public Worker {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Worker name must not be blank");
            }
            if (currentLocation == null) {
                throw new IllegalArgumentException("Worker currentLocation must not be null");
            }
            if (rating < 0.0 || rating > 5.0) {
                throw new IllegalArgumentException("Rating must be in [0, 5], got: " + rating);
            }
            if (activeOrderCount < 0) {
                throw new IllegalArgumentException("activeOrderCount must be >= 0");
            }
            if (preferredServiceTypes == null) {
                preferredServiceTypes = Set.of();
            } else {
                preferredServiceTypes = Set.copyOf(preferredServiceTypes);
            }
        }
    }

    /**
     * A platform consumer (service requester).
     */
    public record Consumer(
            ConsumerId id,
            String name,
            GeoPoint currentLocation
    ) {

        public Consumer {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Consumer name must not be blank");
            }
            if (currentLocation == null) {
                throw new IllegalArgumentException("Consumer currentLocation must not be null");
            }
        }
    }

    /**
     * A consumer's request for a service.
     */
    public record ServiceRequest(
            RequestId id,
            ConsumerId consumerId,
            ServiceType serviceType,
            GeoPoint pickupLocation,
            GeoPoint dropoffLocation
    ) {

        public ServiceRequest {
            if (consumerId == null) {
                throw new IllegalArgumentException("consumerId must not be null");
            }
            if (serviceType == null) {
                throw new IllegalArgumentException("serviceType must not be null");
            }
            if (pickupLocation == null) {
                throw new IllegalArgumentException("pickupLocation must not be null");
            }
            if (dropoffLocation == null) {
                throw new IllegalArgumentException("dropoffLocation must not be null");
            }
        }
    }

    /**
     * Result of matching a worker to a service request.
     */
    public record MatchResult(
            WorkerId workerId,
            RequestId requestId,
            double score
    ) {

        public MatchResult {
            if (workerId == null) {
                throw new IllegalArgumentException("workerId must not be null");
            }
            if (requestId == null) {
                throw new IllegalArgumentException("requestId must not be null");
            }
        }
    }
}
