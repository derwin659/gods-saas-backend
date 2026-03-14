const PANTALLA_ID = "TV-001";
const API_BASE = "http://localhost:8081/api";

let lastEstado = null;

document.addEventListener("DOMContentLoaded", () => {
  iniciarPolling();
});

/* =========================
   POLLING (FUENTE DE VERDAD)
========================= */
function iniciarPolling() {
  setInterval(() => {
    fetch(`${API_BASE}/ia/tv/${PANTALLA_ID}/estado`, {
      headers: { "X-TV-KEY": "TV_SECRET_ABC123" }
    })
      .then(res => {
        // TV libre → intenta tomar sesión
        if (res.status === 204) {
          return fetch(`${API_BASE}/ia/tv/${PANTALLA_ID}/tomarsesion`, {
            method: "POST",
            headers: { "X-TV-KEY": "TV_SECRET_ABC123" }
          }).then(r => (r.status === 204 ? null : r.json()));
        }
        return res.json();
      })
      .then(data => {
        if (!data) {
          mostrarPantalla("pantalla-espera");
          return;
        }

        // evita repintar el mismo estado (excepto generando)
        if (data.estado === lastEstado) {
          return;
        }
        lastEstado = data.estado;


        console.log("📺 Estado:", data.estado, data);

        switch (data.estado) {
          case "MOSTRANDO_EN_TV":
            mostrarAnalitica(data);
            break;

          case "SELECCIONADA":
            mostrarSeleccion(data.seleccionCliente);
            break;

          case "GENERANDO_IMAGEN":
            mostrarPantalla("pantalla-generando");
            break;

          case "IMAGEN_GENERADA":
            mostrarResultado(data);
            break;

          default:
            mostrarPantalla("pantalla-espera");
        }
      })
      .catch(err => console.error("❌ ERROR POLLING", err));
  }, 3000);
}

/* =========================
   UI BÁSICA
========================= */
function mostrarPantalla(idPantalla) {
  document.querySelectorAll(".pantalla").forEach(p =>
    p.classList.remove("visible")
  );
  const el = document.getElementById(idPantalla);
  if (el) el.classList.add("visible");
}

/* =========================
   ANALÍTICA
========================= */
function mostrarAnalitica(resultado) {
  if (!resultado) return;

  mostrarPantalla("pantalla-analitica");

  document.getElementById("formaRostro").innerText =
    resultado.formaRostro?.principal?.toUpperCase() || "";

  const ul = document.getElementById("listaCortes");
  ul.innerHTML = "";

  (resultado.cortesRecomendados || []).forEach(c => {
    const li = document.createElement("li");
    li.innerText = `${c.nombre} (${Math.round(c.score * 100)}%)`;
    ul.appendChild(li);
  });
}

/* =========================
   SELECCIÓN
========================= */
function mostrarSeleccion(seleccion) {
  if (!seleccion) return;

  mostrarPantalla("pantalla-seleccion");

  document.getElementById("selCorte").innerText =
    "✂ Corte: " + (seleccion.corte?.nombre || "-");

  document.getElementById("selTinte").innerText =
    seleccion.tinte?.aplicar
      ? "🎨 Tinte: " + seleccion.tinte.color
      : "🎨 Sin tinte";

  document.getElementById("selOndulado").innerText =
    seleccion.ondulado?.aplicar
      ? "🌀 Ondulado: Sí"
      : "🌀 Ondulado: No";
}

/* =========================
   RESULTADO IA
========================= */
function mostrarResultado(data) {
  if (!data?.imagenes) return;

  if (data.imagenes.antes) {
    document.getElementById("imgAntes").src =
      data.imagenes.antes + "?t=" + Date.now();
  }

  // Si decides guardar lateral también como "antes_lateral"
if (data.imagenes?.anteslateral) {
  const img = document.getElementById("imgAnteslateral");
  if (img) {
    img.src = data.imagenes.anteslateral + "?t=" + Date.now();
  } else {
    console.warn("⚠️ imgAntesLateral no existe en el DOM");
  }
}


  if (data.imagenes.frontal) {
    document.getElementById("imgFrontalIA").src =
      data.imagenes.frontal + "?t=" + Date.now();
  }

  if (data.imagenes.lateral) {
    document.getElementById("imgLateralIA").src =
      data.imagenes.lateral + "?t=" + Date.now();
  }

  if (data.imagenes.trasera) {
    document.getElementById("imgTraseraIA").src =
      data.imagenes.trasera + "?t=" + Date.now();
  }

  mostrarPantalla("pantalla-resultado");
}


/* =========================
   HELPERS
========================= */
function setImg(id, url) {
  if (!url) return;
  const img = document.getElementById(id);
  if (!img) return;
  img.src = url + "?t=" + Date.now(); // evita cache
}
