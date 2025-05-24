package main;

import javax.swing.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@SuppressWarnings("serial")
public class NasaApi extends JFrame {
    // Clave de la API de la NASA
    private static final String API_KEY = "pREcoZO5M8Pt0EPBcaZHQKDPsB6Ht10suiayIKxX";
    private static final DateTimeFormatter APOD_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final LocalDate APOD_API_START_DATE = LocalDate.of(1995, 6, 16); // First APOD was 1995-06-16

    // Componentes de la interfaz
    private JTextArea textArea;
    private JPanel centerPanel;
    private CardLayout cardLayout;
    private JLabel imageLabel; // para mostrar una imagen individual
    private JScrollPane mosaicScrollPane; // Scroll pane for mosaic
    private JTextField apodDateText;
    private JButton apodSearchButton;
    private JButton prevApodButton;
    private JButton nextApodButton;
    private LocalDate currentApodDate; // Date of the currently displayed APOD


    public NasaApi() {
        setTitle("Consultas a la API de la NASA");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Panel superior con botones
        JPanel buttonPanel = new JPanel();
        JButton apodButton = new JButton("Consultar APOD");
        // Nuevos componentes para la búsqueda de APOD por fecha
        apodDateText = new JTextField(10); // Campo para la fecha
        apodDateText.setToolTipText("Ingrese fecha como AAAA-MM-DD");
        apodSearchButton = new JButton("Buscar APOD por Fecha"); // Botón para buscar por fecha
        prevApodButton = new JButton("Día Anterior");
        nextApodButton = new JButton("Día Siguiente");

        prevApodButton.setEnabled(false); // Initially disabled
        nextApodButton.setEnabled(false); // Initially disabled

        buttonPanel.add(apodButton);
        buttonPanel.add(new JLabel("Fecha (AAAA-MM-DD):")); // Etiqueta para el campo de fecha
        buttonPanel.add(apodDateText);
        buttonPanel.add(apodSearchButton);
        buttonPanel.add(prevApodButton);
        buttonPanel.add(nextApodButton);

        JButton roverButton = new JButton("Consultar Mars Rover Photos");
        buttonPanel.add(roverButton);

        // Panel central con CardLayout
        cardLayout = new CardLayout();
        centerPanel = new JPanel(cardLayout);

        // Tarjeta de texto para errores o mensajes
        textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane textScrollPane = new JScrollPane(textArea);
        centerPanel.add(textScrollPane, "TEXT");

        // Tarjeta para mostrar una imagen individual
        imageLabel = new JLabel();
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        centerPanel.add(new JScrollPane(imageLabel), "IMAGE");

        // Tarjeta para el mosaico de imágenes del rover
        mosaicScrollPane = new JScrollPane();
        centerPanel.add(mosaicScrollPane, "MOSAIC");

        add(buttonPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        // Acción para el botón APOD
        apodButton.addActionListener(e -> fetchAndProcessApod(null)); // Fetch current day's APOD

        // Acción para el nuevo botón de búsqueda de APOD por fecha
        apodSearchButton.addActionListener(e -> {
            String dateString = apodDateText.getText().trim();
            if (!dateString.matches("\\d{4}-\\d{2}-\\d{2}")) {
                SwingUtilities.invokeLater(() -> {
                    textArea.setText("Formato de fecha incorrecto. Use AAAA-MM-DD.");
                    cardLayout.show(centerPanel, "TEXT");
                    updateNavigationButtonStates(); // Keep nav buttons updated even on error
                });
                return;
            }
            try {
                LocalDate requested = LocalDate.parse(dateString, APOD_DATE_FORMATTER);
                if (requested.isAfter(LocalDate.now())) {
                     SwingUtilities.invokeLater(() -> {
                        textArea.setText("No se pueden solicitar fechas futuras para APOD.");
                        cardLayout.show(centerPanel, "TEXT");
                        updateNavigationButtonStates();
                    });
                    return;
                }
                if (requested.isBefore(APOD_API_START_DATE)) {
                     SwingUtilities.invokeLater(() -> {
                        textArea.setText("La primera imagen APOD es del " + APOD_API_START_DATE.format(APOD_DATE_FORMATTER) + ".");
                        cardLayout.show(centerPanel, "TEXT");
                        updateNavigationButtonStates();
                    });
                    return;
                }
            } catch (DateTimeParseException ex) {
                // This should ideally not happen due to regex, but good practice
                SwingUtilities.invokeLater(() -> {
                    textArea.setText("Error al parsear la fecha: " + ex.getMessage());
                    cardLayout.show(centerPanel, "TEXT");
                    updateNavigationButtonStates();
                });
                return;
            }
            fetchAndProcessApod(dateString);
        });

        // Acción para el botón "Día Anterior"
        prevApodButton.addActionListener(e -> {
            if (currentApodDate != null) {
                LocalDate prevDate = currentApodDate.minusDays(1);
                fetchAndProcessApod(prevDate.format(APOD_DATE_FORMATTER));
            }
        });

        // Acción para el botón "Día Siguiente"
        nextApodButton.addActionListener(e -> {
            if (currentApodDate != null) {
                LocalDate nextDate = currentApodDate.plusDays(1);
                fetchAndProcessApod(nextDate.format(APOD_DATE_FORMATTER));
            }
        });

        // Acción para el botón de fotos del rover
        roverButton.addActionListener(new ActionListener() {
            @SuppressWarnings("deprecation")
			public void actionPerformed(ActionEvent e) {
                new Thread(() -> {
                    String url = "https://api.nasa.gov/mars-photos/api/v1/rovers/curiosity/photos?sol=1000&api_key=" + API_KEY;
                    String response = getApiResponse(url);
                    try {
                        JSONObject json = new JSONObject(response);
                        JSONArray photos = json.getJSONArray("photos");
                        if (photos.length() > 0) {
                            if (photos.length() == 1) {
                                // Si hay una sola foto, se muestra de forma individual
                                JSONObject photo = photos.getJSONObject(0);
                                String imageUrl = photo.getString("img_src");
                                ImageIcon icon = new ImageIcon(new URL(imageUrl));
                                SwingUtilities.invokeLater(() -> {
                                    imageLabel.setIcon(icon);
                                    cardLayout.show(centerPanel, "IMAGE");
                                });
                            } else {
                                // Si hay varias fotos, se crea un mosaico
                                JPanel mosaicPanel = new JPanel();
                                int columns = 3;
                                int rows = (int) Math.ceil(photos.length() / (double) columns);
                                mosaicPanel.setLayout(new GridLayout(rows, columns, 5, 5));
                                
                                // Se añaden todas las imágenes al panel en miniatura
                                for (int i = 0; i < photos.length(); i++) {
                                    JSONObject photo = photos.getJSONObject(i);
                                    String imageUrl = photo.getString("img_src");
                                    ImageIcon icon = new ImageIcon(new URL(imageUrl));
                                    // Escalar la imagen a un tamaño fijo (ej. 250x250 píxeles)
                                    Image image = icon.getImage();
                                    Image scaledImage = image.getScaledInstance(250, 250, Image.SCALE_SMOOTH);
                                    JLabel picLabel = new JLabel(new ImageIcon(scaledImage));
                                    picLabel.setHorizontalAlignment(SwingConstants.CENTER);
                                    mosaicPanel.add(picLabel);
                                }
                                // Se coloca el mosaico en un JScrollPane para que sea desplazable
                                // JScrollPane mosaicScrollPane = new JScrollPane(mosaicPanel); // Removed local declaration
                                SwingUtilities.invokeLater(() -> {
                                    // centerPanel.add(mosaicScrollPane, "MOSAIC"); // Removed, added in constructor
                                    NasaApi.this.mosaicScrollPane.setViewportView(mosaicPanel); // Update existing scroll pane
                                    cardLayout.show(centerPanel, "MOSAIC");
                                });
                            }
                        } else {
                            SwingUtilities.invokeLater(() -> {
                                textArea.setText("No se encontraron fotos en la consulta.");
                                cardLayout.show(centerPanel, "TEXT");
                            });
                        }
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            textArea.setText("Error al parsear la respuesta: " + ex.getMessage());
                            cardLayout.show(centerPanel, "TEXT");
                        });
                    }
                }).start();
            }
        });
    }

    private void fetchAndProcessApod(String dateStringOrNull) {
        new Thread(() -> {
            String url;
            if (dateStringOrNull == null) { // Fetch current day's APOD
                url = "https://api.nasa.gov/planetary/apod?api_key=" + API_KEY;
            } else { // Fetch APOD for specific date
                url = "https://api.nasa.gov/planetary/apod?api_key=" + API_KEY + "&date=" + dateStringOrNull;
            }
            String response = getApiResponse(url);
            processAndDisplayApod(response, dateStringOrNull);
        }).start();
    }

    private void updateNavigationButtonStates() {
        if (currentApodDate == null) {
            prevApodButton.setEnabled(false);
            nextApodButton.setEnabled(false);
        } else {
            // Enable previous button if current date minus one day is not before APOD start date
            prevApodButton.setEnabled(!currentApodDate.minusDays(1).isBefore(APOD_API_START_DATE));
            // Enable next button if current date is before today
            nextApodButton.setEnabled(currentApodDate.isBefore(LocalDate.now()));
        }
    }

    // Método refactorizado para procesar y mostrar la respuesta de APOD
    @SuppressWarnings("deprecation")
    private void processAndDisplayApod(String response, String requestedDateString) {
        try {
            if (response.startsWith("Error:")) {
                SwingUtilities.invokeLater(() -> {
                    textArea.setText(response);
                    cardLayout.show(centerPanel, "TEXT");
                    // currentApodDate no se actualiza en error, pero los botones sí
                    // para reflejar que la última operación falló.
                    // Si requestedDateString es válido, podríamos intentar restaurar currentApodDate,
                    // pero es más simple deshabilitarlos o basarlos en el estado actual.
                    // Por ahora, si APOD falla, no cambiamos currentApodDate y actualizamos botones.
                    updateNavigationButtonStates(); 
                });
                return;
            }

            JSONObject json = new JSONObject(response);

            if (json.has("msg") || (json.has("code") && json.getInt("code") != 200)) {
                String errorMsg = json.has("msg") ? json.getString("msg") : "Error desconocido de la API.";
                int code = json.has("code") ? json.getInt("code") : -1;
                SwingUtilities.invokeLater(() -> {
                    textArea.setText("Error de la API (" + code + "): " + errorMsg);
                    cardLayout.show(centerPanel, "TEXT");
                    updateNavigationButtonStates(); // Actualizar estado de botones
                });
                return;
            }
            
            String dateFromJson = json.getString("date");
            this.currentApodDate = LocalDate.parse(dateFromJson, APOD_DATE_FORMATTER);
            // Actualizar el campo de texto si la fecha es diferente de la solicitada
            // o si se solicitó el APOD del día actual (requestedDateString == null)
            if (requestedDateString == null || !requestedDateString.equals(dateFromJson)) {
                apodDateText.setText(dateFromJson);
            }


            String mediaType = json.getString("media_type");
            String title = json.getString("title");
            String explanation = json.getString("explanation");

            if (mediaType.equals("image")) {
                String imageUrl = json.getString("url");
                ImageIcon icon = new ImageIcon(new URL(imageUrl));
                SwingUtilities.invokeLater(() -> {
                    imageLabel.setIcon(icon);
                    imageLabel.setToolTipText("<html>"
                            + "<h1>" + title + "</h1>"
                            + "<p width=\"400px\">" + explanation + "</p>"
                            + "<hr><p><em>(Título y descripción en inglés proporcionados por NASA.)</em></p>"
                            + "</html>");
                    cardLayout.show(centerPanel, "IMAGE");
                    updateNavigationButtonStates(); // Actualizar estado de botones
                });
            } else {
                SwingUtilities.invokeLater(() -> {
                    StringBuilder details = new StringBuilder();
                    details.append("El contenido APOD del día (" + dateFromJson + ") no es una imagen.\n");
                    details.append("Título: ").append(title).append("\n");
                    details.append("Tipo de medio: ").append(mediaType).append("\n");
                    if (json.has("url")) {
                        details.append("URL del contenido: ").append(json.getString("url")).append("\n");
                    }
                    details.append("Explicación: ").append(explanation);
                    textArea.setText(details.toString());
                    cardLayout.show(centerPanel, "TEXT");
                    updateNavigationButtonStates(); // Actualizar estado de botones
                });
            }
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> {
                if (response.length() < 200 && !response.trim().startsWith("{")) {
                     textArea.setText("Error al procesar la respuesta de APOD.\nRespuesta recibida:\n" + response);
                } else {
                     textArea.setText("Error al parsear la respuesta JSON de APOD: " + ex.getMessage());
                }
                cardLayout.show(centerPanel, "TEXT");
                updateNavigationButtonStates(); // Actualizar estado de botones
            });
        }
    }

    // Método para realizar la consulta HTTP a la API
    @SuppressWarnings("deprecation")
	private String getApiResponse(String urlString) {
        StringBuilder result = new StringBuilder();
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            
            int status = conn.getResponseCode();
            Reader streamReader;
            // Si el código de estado es un error (4xx o 5xx)
            // NASA API a veces devuelve JSON con detalles del error incluso con estos códigos.
            // Otras veces, devuelve HTML (ej. para 404 Not Found directo del servidor, no de la API)
            // Leeremos errorStream si está disponible, sino inputStream.
            if (status >= 400) { // Consideramos 4xx y 5xx como errores
                streamReader = conn.getErrorStream();
                 // Si getErrorStream() es null (puede pasar si no hay cuerpo de error),
                 // no hay nada que leer, pero necesitamos registrar que hubo un error.
                if (streamReader == null) {
                    // Devolvemos un mensaje de error genérico con el código de estado.
                    // El método processAndDisplayApod intentará parsear esto.
                    // Si no es JSON, lo mostrará directamente.
                    return "Error: HTTP " + status + " " + conn.getResponseMessage();
                }
            } else {
                streamReader = new InputStreamReader(conn.getInputStream());
            }

            BufferedReader in = new BufferedReader(streamReader);
            String line;
            while ((line = in.readLine()) != null) {
                result.append(line).append("\n");
            }
            in.close();
        } catch (IOException e) {
            // Para errores de conexión, etc.
            return "Error: " + e.getMessage();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return result.toString();
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new NasaApi().setVisible(true);
        });
    }
}
