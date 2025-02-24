package main;

import javax.swing.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

@SuppressWarnings("serial")
public class NasaApi extends JFrame {
    // Clave de la API de la NASA
    private static final String API_KEY = "pREcoZO5M8Pt0EPBcaZHQKDPsB6Ht10suiayIKxX";
    
    // Componentes de la interfaz
    private JTextArea textArea;
    private JPanel centerPanel;
    private CardLayout cardLayout;
    private JLabel imageLabel; // para mostrar una imagen individual

    public NasaApi() {
        setTitle("Consultas a la API de la NASA");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Panel superior con botones
        JPanel buttonPanel = new JPanel();
        JButton apodButton = new JButton("Consultar APOD");
        JButton roverButton = new JButton("Consultar Mars Rover Photos");
        buttonPanel.add(apodButton);
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

        add(buttonPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);

        // Acción para el botón APOD
        apodButton.addActionListener(new ActionListener() {
            @SuppressWarnings("deprecation")
			public void actionPerformed(ActionEvent e) {
                new Thread(() -> {
                    String url = "https://api.nasa.gov/planetary/apod?api_key=" + API_KEY;
                    String response = getApiResponse(url);
                    try {
                        JSONObject json = new JSONObject(response);
                        String mediaType = json.getString("media_type");
                        String explanation = json.getString("explanation");
                        if (mediaType.equals("image")) {
                            String imageUrl = json.getString("url");
                            ImageIcon icon = new ImageIcon(new URL(imageUrl));
                            SwingUtilities.invokeLater(() -> {
                                imageLabel.setIcon(icon);
                                imageLabel.setToolTipText("<html><p width=\"400px\">" + explanation + "</p></html>");
                                cardLayout.show(centerPanel, "IMAGE");
                            });
                        } else {
                            SwingUtilities.invokeLater(() -> {
                                textArea.setText("El contenido no es una imagen:\n" + response);
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
                                JScrollPane mosaicScrollPane = new JScrollPane(mosaicPanel);
                                SwingUtilities.invokeLater(() -> {
                                    centerPanel.add(mosaicScrollPane, "MOSAIC");
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

    // Método para realizar la consulta HTTP a la API
    @SuppressWarnings("deprecation")
	private String getApiResponse(String urlString) {
        StringBuilder result = new StringBuilder();
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            
            int status = conn.getResponseCode();
            Reader streamReader;
            if (status > 299) {
                streamReader = new InputStreamReader(conn.getErrorStream());
            } else {
                streamReader = new InputStreamReader(conn.getInputStream());
            }
            BufferedReader in = new BufferedReader(streamReader);
            String line;
            while ((line = in.readLine()) != null) {
                result.append(line).append("\n");
            }
            in.close();
            conn.disconnect();
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
        return result.toString();
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new NasaApi().setVisible(true);
        });
    }
}
