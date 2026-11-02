
import { useCallback, useEffect, useMemo, useState } from "react";
import "./App.css";
let authToken = null;

const API = "http://localhost:8080/api";

async function apiRequest(path, options = {}) {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: {
      ...(options.body
        ? { "Content-Type": "application/json" }
        : {}),
      ...(authToken
        ? { Authorization: `Bearer ${authToken}` }
        : {}),
      ...options.headers,
    },
  });

  const text = await response.text();
  let data = null;

  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    throw new Error("The server returned an invalid response.");
  }

if (!response.ok) {
  const requestId =
    data?.requestId || response.headers.get("X-Request-ID");

  const message =
    data?.error || `Request failed with status ${response.status}`;

  throw new Error(
    requestId ? `${message} (Request ID: ${requestId})` : message
  );
}

  return data;
  }

 





function SeverityBadge({ severity }) {
  return (
    <span className={`severity severity-${severity?.toLowerCase()}`}>
      {severity}
    </span>
  );
}

export default function App() {
  const [incidents, setIncidents] = useState([]);
  const [health, setHealth] = useState("Checking");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [search, setSearch] = useState("");
  const [severityFilter, setSeverityFilter] = useState("ALL");
  const [incidentServiceId, setIncidentServiceId] = useState("S001");
  const [serviceId, setServiceId] = useState("S001");
  const [downstream, setDownstream] = useState([]);
  const [correlationIncidentId, setCorrelationIncidentId] = useState("");
  const [correlationForm, setCorrelationForm] = useState({
    traceId: "",
    serviceName: "",
    operationName: "",
    errorType: "",
    message: "",
  });
  const [correlationResult, setCorrelationResult] = useState(null);
  const [correlationBusy, setCorrelationBusy] = useState(false);

  const [authenticated, setAuthenticated] = useState(false);
const [loginForm, setLoginForm] = useState({
  username: "",
  password: "",
});
const [loggingIn, setLoggingIn] = useState(false);

async function handleLogin(event) {
  event.preventDefault();
  setError("");
  setMessage("");
  setLoggingIn(true);

  try {
    const result = await apiRequest("/auth/login", {
      method: "POST",
      body: JSON.stringify(loginForm),
    });

    authToken = result.token;
    setAuthenticated(true);
    setLoginForm({ username: "", password: "" });
    setMessage("Successfully signed in.");
  } catch (err) {
    setError(err.message || "Login failed.");
  } finally {
    setLoggingIn(false);
  }
}

async function handleLogout() {
  try {
    await apiRequest("/auth/logout", {
      method: "POST",
      body: "{}",
    });
  } catch {
    // Clear the local session even if the server is unavailable.
  } finally {
    authToken = null;
    setAuthenticated(false);
    setIncidents([]);
    setDownstream([]);
    setCorrelationIncidentId("");
    setCorrelationResult(null);
    setCorrelationForm({
      traceId: "",
      serviceName: "",
      operationName: "",
      errorType: "",
      message: "",
    });
    setMessage("You have been logged out.");
    setError("");
  }
}

  const [form, setForm] = useState({
    id: "",
    description: "",
    severity: "HIGH",
  });

  const loadDashboard = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const [healthResult, incidentResult] = await Promise.all([
        apiRequest("/health"),
        apiRequest("/incidents"),
      ]);

      setHealth(healthResult.status);
      setIncidents(incidentResult);
    } catch (err) {
      setHealth("Unavailable");
      setError(err.message || "Unable to load the dashboard.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (authenticated) {
      loadDashboard();
    }
  }, [authenticated, loadDashboard]);

  const counts = useMemo(() => {
    const result = {
      total: incidents.length,
      CRITICAL: 0,
      HIGH: 0,
      MEDIUM: 0,
      LOW: 0,
    };

    incidents.forEach((incident) => {
      if (incident.severity in result) {
        result[incident.severity] += 1;
      }
    });

    return result;
  }, [incidents]);

  const filteredIncidents = useMemo(() => {
    const query = search.trim().toLowerCase();

    return incidents.filter((incident) => {
      const matchesSearch =
        incident.id.toLowerCase().includes(query) ||
        incident.description.toLowerCase().includes(query);

      const matchesSeverity =
        severityFilter === "ALL" ||
        incident.severity === severityFilter;

      return matchesSearch && matchesSeverity;
    });
  }, [incidents, search, severityFilter]);

  async function createIncident(event) {
    event.preventDefault();
    setSubmitting(true);
    setError("");
    setMessage("");

    try {
      await apiRequest("/incidents", {
        method: "POST",
        body: JSON.stringify({
          id: form.id.trim(),
          serviceId: incidentServiceId,
          description: form.description.trim(),
          severity: form.severity,
        }),
      });

      setForm({
        id: "",
        description: "",
        severity: "HIGH",
      });

      setMessage("Incident created and saved to PostgreSQL.");
      await loadDashboard();
    } catch (err) {
      setError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function analyzeIncident(id) {
    setError("");
    setMessage("");

    try {
      await apiRequest(
        `/incidents/${encodeURIComponent(id)}/analyze`,
        { method: "POST", body: "{}" }
      );

      setMessage(`Analysis accepted for ${id}.`);
    } catch (err) {
      setError(err.message);
    }
  }

  async function inspectDependencies(event) {
    event.preventDefault();
    setError("");
    setDownstream([]);

    try {
      const result = await apiRequest(
        `/services/${encodeURIComponent(serviceId)}/downstream`
      );

      setDownstream(result.downstream);
      setMessage(`Loaded dependencies for ${serviceId}.`);
    } catch (err) {
      setError(err.message);
    }
  }

  function openCorrelationForm(incidentId) {
    setCorrelationIncidentId(incidentId);
    setCorrelationResult(null);
    setCorrelationForm({
      traceId: "",
      serviceName: "",
      operationName: "",
      errorType: "",
      message: "",
    });
    setError("");
    setMessage("");
  }

  async function linkCorrelation(event) {
    event.preventDefault();
    setError("");
    setMessage("");
    setCorrelationBusy(true);

    try {
      const result = await apiRequest(
        `/incidents/${encodeURIComponent(correlationIncidentId)}/correlation`,
        {
          method: "POST",
          body: JSON.stringify({
            traceId: correlationForm.traceId.trim(),
            serviceName: correlationForm.serviceName.trim(),
            operationName: correlationForm.operationName.trim(),
            errorType: correlationForm.errorType.trim(),
            message: correlationForm.message.trim(),
          }),
        }
      );

      setCorrelationResult(result);
      setMessage(`Trace correlation linked to ${correlationIncidentId}.`);
    } catch (err) {
      setError(err.message || "Unable to link trace correlation.");
    } finally {
      setCorrelationBusy(false);
    }
  }

  async function retrieveCorrelation() {
    setError("");
    setMessage("");
    setCorrelationResult(null);
    setCorrelationBusy(true);

    try {
      const result = await apiRequest(
        `/incidents/${encodeURIComponent(correlationIncidentId)}/correlation`
      );

      setCorrelationResult(result);
      setMessage(`Trace correlation loaded for ${correlationIncidentId}.`);
    } catch (err) {
      setError(err.message || "Unable to retrieve trace correlation.");
    } finally {
      setCorrelationBusy(false);
    }
  }

  if (!authenticated) {
    return (
      <main className="login-shell">
        <form className="login-panel" onSubmit={handleLogin}>
          <div className="brand-mark">N</div>
          <p className="eyebrow">NEXUSAI SECURITY</p>
          <h1>Sign in</h1>
          <p className="subtitle">
            Authenticate to access the Operations Center.
          </p>

          {error && (
            <div className="notice notice-error" role="alert">
              {error}
            </div>
          )}

          <label>
            Username
            <input
              autoComplete="username"
              value={loginForm.username}
              onChange={(event) =>
                setLoginForm({
                  ...loginForm,
                  username: event.target.value,
                })
              }
              required
            />
          </label>

          <label>
            Password
            <input
              type="password"
              autoComplete="current-password"
              value={loginForm.password}
              onChange={(event) =>
                setLoginForm({
                  ...loginForm,
                  password: event.target.value,
                })
              }
              required
            />
          </label>

          <button
            className="button button-primary"
            disabled={loggingIn}
          >
            {loggingIn ? "Signing in..." : "Sign in"}
          </button>
        </form>
      </main>
    );
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="/">
          <span className="brand-mark">N</span>
          <span>
            <strong>NexusAI</strong>
            <small>Operations Center</small>
          </span>
        </a>

        <div className="connection">
          <span
            className={`status-dot ${
              health === "UP" ? "online" : "offline"
            }`}
          />
          API: {health}
          <button
            className="button button-secondary"
            onClick={loadDashboard}
            disabled={loading}
          >
            Refresh
          </button>
          <button
            className="button button-secondary"
            onClick={handleLogout}
          >
            Log out
          </button>
        </div>
      </header>

      <section className="page-heading">
        <div>
          <p className="eyebrow">SYSTEM OVERVIEW</p>
          <h1>Incident dashboard</h1>
          <p className="subtitle">
            Monitor incidents, inspect dependencies, and trigger analysis.
          </p>
        </div>
      </section>

      {error && (
        <div className="notice notice-error" role="alert">
          {error}
        </div>
      )}

      {message && (
        <div className="notice notice-success" role="status">
          {message}
        </div>
      )}

      <section className="stats-grid">
        <StatCard label="Total incidents" value={counts.total} />
        <StatCard
          label="Critical"
          value={counts.CRITICAL}
          accent="critical"
        />
        <StatCard label="High" value={counts.HIGH} accent="high" />
        <StatCard label="Medium" value={counts.MEDIUM} accent="medium" />
        <StatCard label="Low" value={counts.LOW} accent="low" />
      </section>

      <section className="content-grid">
        <div className="panel">
          <div className="panel-heading">
            <div>
              <h2>Create incident</h2>
              <p>Save a new incident in PostgreSQL.</p>
            </div>
          </div>

          <form className="incident-form" onSubmit={createIncident}>
            <label>
              Incident ID
              <input
                value={form.id}
                onChange={(event) =>
                  setForm({ ...form, id: event.target.value })
                }
                placeholder="INC-1001"
                maxLength={64}
                required
              />
            </label>

            <label>
              Service
              <select
                value={incidentServiceId}
                onChange={(event) =>
                  setIncidentServiceId(event.target.value)
                }
              >
                <option value="S001">S001 — API Gateway</option>
                <option value="S002">S002 — Payment Service</option>
                <option value="S003">S003 — Database</option>
                <option value="S004">S004 — Notifications</option>
              </select>
            </label>

            <label>
              Description
              <textarea
                value={form.description}
                onChange={(event) =>
                  setForm({ ...form, description: event.target.value })
                }
                placeholder="Describe the incident..."
                maxLength={1000}
                rows={3}
                required
              />
            </label>

            <label>
              Severity
              <select
                value={form.severity}
                onChange={(event) =>
                  setForm({ ...form, severity: event.target.value })
                }
              >
                <option value="CRITICAL">Critical</option>
                <option value="HIGH">High</option>
                <option value="MEDIUM">Medium</option>
                <option value="LOW">Low</option>
              </select>
            </label>

            <button className="button button-primary" disabled={submitting}>
              {submitting ? "Saving..." : "Create incident"}
            </button>
          </form>
        </div>

        <div className="panel">
          <div className="panel-heading">
            <div>
              <h2>Service dependencies</h2>
              <p>Explore the downstream services in your graph.</p>
            </div>
          </div>

          <form className="dependency-form" onSubmit={inspectDependencies}>
            <label>
              Service ID
              <select
                value={serviceId}
                onChange={(event) => setServiceId(event.target.value)}
              >
                <option value="S001">S001 — API Gateway</option>
                <option value="S002">S002 — Payment Service</option>
                <option value="S003">S003 — Database</option>
                <option value="S004">S004 — Notifications</option>
              </select>
            </label>

            <button className="button button-secondary">
              Inspect dependencies
            </button>
          </form>

          <div className="dependency-results">
            {downstream.length > 0 ? (
              downstream.map((id) => (
                <span className="dependency-chip" key={id}>
                  {id}
                </span>
              ))
            ) : (
              <p className="empty-hint">
                No dependency results yet, or no downstream services found.
              </p>
            )}
          </div>
        </div>
      </section>

      <section className="panel incidents-panel">
        <div className="panel-heading table-heading">
          <div>
            <h2>Incidents</h2>
            <p>Records retrieved from the Java API and PostgreSQL.</p>
          </div>
          <span className="record-count">
            {filteredIncidents.length} records
          </span>
        </div>

        <div className="filters">
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search ID or description"
            aria-label="Search incidents"
          />

          <select
            value={severityFilter}
            onChange={(event) => setSeverityFilter(event.target.value)}
            aria-label="Filter by severity"
          >
            <option value="ALL">All severities</option>
            <option value="CRITICAL">Critical</option>
            <option value="HIGH">High</option>
            <option value="MEDIUM">Medium</option>
            <option value="LOW">Low</option>
          </select>
        </div>

        {loading ? (
          <p className="empty-state">Loading incidents...</p>
        ) : filteredIncidents.length === 0 ? (
          <p className="empty-state">
            No matching incidents. Create one above to get started.
          </p>
        ) : (
          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Incident ID</th>
                  <th>Description</th>
                  <th>Severity</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredIncidents.map((incident) => (
                  <tr key={incident.id}>
                    <td className="incident-id">{incident.id}</td>
                    <td>{incident.description}</td>
                    <td>
                      <SeverityBadge severity={incident.severity} />
                    </td>
                    <td>
                      <button
                        className="button button-small button-secondary"
                        onClick={() => analyzeIncident(incident.id)}
                      >
                        Analyze
                      </button>
                      <button
                        className="button button-small button-secondary"
                        onClick={() => openCorrelationForm(incident.id)}
                      >
                        Correlation
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {correlationIncidentId && (
          <div className="correlation-panel">
            <div className="panel-heading">
              <div>
                <h2>Incident correlation</h2>
                <p>
                  Link or retrieve trace details for{" "}
                  <strong>{correlationIncidentId}</strong>.
                </p>
              </div>
              <button
                className="button button-secondary"
                type="button"
                onClick={() => {
                  setCorrelationIncidentId("");
                  setCorrelationResult(null);
                  setCorrelationForm({
                    traceId: "",
                    serviceName: "",
                    operationName: "",
                    errorType: "",
                    message: "",
                  });
                }}
              >
                Close
              </button>
            </div>

            <form className="correlation-form" onSubmit={linkCorrelation}>
              <label>
                Trace ID
                <input
                  value={correlationForm.traceId}
                  onChange={(event) =>
                    setCorrelationForm({
                      ...correlationForm,
                      traceId: event.target.value,
                    })
                  }
                  placeholder="32-character hexadecimal trace ID"
                  pattern="[a-fA-F0-9]{32}"
                  title="Enter a 32-character hexadecimal trace ID"
                  required
                />
              </label>

              <label>
                Service name
                <input
                  value={correlationForm.serviceName}
                  onChange={(event) =>
                    setCorrelationForm({
                      ...correlationForm,
                      serviceName: event.target.value,
                    })
                  }
                  required
                />
              </label>

              <label>
                Operation name
                <input
                  value={correlationForm.operationName}
                  onChange={(event) =>
                    setCorrelationForm({
                      ...correlationForm,
                      operationName: event.target.value,
                    })
                  }
                  required
                />
              </label>

              <label>
                Error type
                <input
                  value={correlationForm.errorType}
                  onChange={(event) =>
                    setCorrelationForm({
                      ...correlationForm,
                      errorType: event.target.value,
                    })
                  }
                  required
                />
              </label>

              <label>
                Message
                <textarea
                  value={correlationForm.message}
                  onChange={(event) =>
                    setCorrelationForm({
                      ...correlationForm,
                      message: event.target.value,
                    })
                  }
                  rows={2}
                  required
                />
              </label>

              <div className="correlation-actions">
                <button
                  className="button button-primary"
                  disabled={correlationBusy}
                >
                  {correlationBusy ? "Linking..." : "Link trace"}
                </button>
                <button
                  className="button button-secondary"
                  type="button"
                  onClick={retrieveCorrelation}
                  disabled={correlationBusy}
                >
                  {correlationBusy ? "Loading..." : "Retrieve correlation"}
                </button>
              </div>
            </form>

            {correlationResult && (
              <dl className="correlation-result" aria-live="polite">
                <div>
                  <dt>Incident</dt>
                  <dd>{correlationResult.incidentId}</dd>
                </div>
                <div>
                  <dt>Trace ID</dt>
                  <dd>{correlationResult.traceId}</dd>
                </div>
                <div>
                  <dt>Service</dt>
                  <dd>{correlationResult.serviceName}</dd>
                </div>
                <div>
                  <dt>Operation</dt>
                  <dd>{correlationResult.operationName}</dd>
                </div>
                <div>
                  <dt>Error type</dt>
                  <dd>{correlationResult.errorType}</dd>
                </div>
                <div>
                  <dt>Message</dt>
                  <dd>{correlationResult.message}</dd>
                </div>
              </dl>
            )}
          </div>
        )}
      </section>

      <footer className="footer">
        NexusAI · Java · React · PostgreSQL
      </footer>
    </main>
  );
}

function StatCard({ label, value, accent = "" }) {
  return (
    <article className={`stat-card ${accent}`}>
      <p>{label}</p>
      <strong>{value}</strong>
    </article>
  );
}
